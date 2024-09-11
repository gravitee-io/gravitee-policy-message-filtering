/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.messagefiltering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageRequest;
import io.gravitee.gateway.reactive.api.context.MessageResponse;
import io.gravitee.gateway.reactive.api.message.DefaultMessage;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.policy.messagefiltering.configuration.MessageFilteringPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MessageFilteringPolicyTest {

    @Captor
    ArgumentCaptor<Function<Message, Maybe<Message>>> messageCaptor;

    @Mock
    private MessageRequest request;

    @Mock
    private MessageResponse response;

    @Mock
    private MessageExecutionContext ctx;

    @Mock
    private TemplateEngine templateEngine;

    private MessageFilteringPolicy cut;
    private MessageFilteringPolicyConfiguration configuration;

    @BeforeEach
    public void init() {
        lenient().when(ctx.getTemplateEngine()).thenReturn(templateEngine);
        lenient().when(ctx.getTemplateEngine(any())).thenReturn(templateEngine);
        lenient().when(ctx.request()).thenReturn(request);
        lenient().when(ctx.response()).thenReturn(response);
        configuration = new MessageFilteringPolicyConfiguration();
        cut = new MessageFilteringPolicy(configuration);
    }

    @Test
    void should_return_id() {
        assertThat(cut.id()).isEqualTo("message-filtering");
    }

    @Nested
    class RequestMessages {

        @Test
        void should_not_filter_request_messages_when_expression_is_true() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenReturn(true);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void should_filter_request_messages_when_expression_is_false() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
            when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertComplete();
        }

        @Test
        void should_not_filter_request_messages_on_sub_expression_when_expression_contains_an_expression() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
            when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(true);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void should_filter_request_messages_on_sub_expression_when_expression_contains_an_expression() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
            when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertComplete();
        }

        @Test
        void should_not_ack_filtered_request_messages_when_expression_is_true_and_ackFilteredMessage_is_false() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenReturn(true);
            configuration.setAckFilteredMessage(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_ack_filter_request_messages_when_expression_is_false_and_ackFilteredMessage_is_true() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
            when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message).ack();
        }

        @Test
        void should_not_filter_request_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setFilterMessageOnFilteringError(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_not_filtered_and_not_ack_request_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false_and_ackFilteredMessage_is_false() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setAckFilteredMessage(false);
            configuration.setFilterMessageOnFilteringError(false);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_filter_and_ack_request_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_true() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setFilterMessageOnFilteringError(true);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message).ack();
        }

        @Test
        void should_not_filtered_and_ack_request_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false_and_ackFilteredMessage_is_false() {
            when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setAckFilteredMessage(false);
            configuration.setFilterMessageOnFilteringError(true);
            cut.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message, never()).ack();
        }
    }

    @Nested
    class ResponseMessage {

        @Test
        void should_not_filter_response_messages_when_expression_is_true() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenReturn(true);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void should_filter_response_messages_when_expression_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
            when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertComplete();
        }

        @Test
        void should_not_filter_response_messages_on_sub_expression_when_expression_contains_an_expression() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
            when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(true);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void should_filter_response_messages_on_sub_expression_when_expression_contains_an_expression() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
            when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
            messageCaptor.getValue().apply(message).test().assertComplete();
        }

        @Test
        void should_not_ack_filtered_response_messages_when_expression_is_true_and_ackFilteredMessage_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenReturn(true);
            configuration.setAckFilteredMessage(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_ack_filter_response_messages_when_expression_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
            when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message).ack();
        }

        @Test
        void should_not_filter_response_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setFilterMessageOnFilteringError(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_not_filtered_and_not_ack_response_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false_and_ackFilteredMessage_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setAckFilteredMessage(false);
            configuration.setFilterMessageOnFilteringError(false);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertValue(message);
            verify(message, never()).ack();
        }

        @Test
        void should_filter_and_ack_response_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_true() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setFilterMessageOnFilteringError(true);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message).ack();
        }

        @Test
        void should_not_filtered_and_ack_response_messages_when_expression_failed_and_FilterMessageOnFilteringError_is_false_and_ackFilteredMessage_is_false() {
            when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
            when(templateEngine.getValue(any(), any())).thenThrow(new RuntimeException());
            configuration.setAckFilteredMessage(false);
            configuration.setFilterMessageOnFilteringError(true);
            cut.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());

            DefaultMessage message = spy(DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build());
            messageCaptor.getValue().apply(message).test().assertComplete();
            verify(message, never()).ack();
        }
    }
}
