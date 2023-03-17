/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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
    void shouldReturnId() {
        assertThat(cut.id()).isEqualTo("message-filtering");
    }

    @Test
    void shouldNotFilterRequestMessagesWhenExpressionIsTrue() {
        when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), any())).thenReturn(true);
        cut.onMessageRequest(ctx).test().assertComplete();
        verify(request).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertValue(message);
    }

    @Test
    void shouldFilterRequestMessagesWhenExpressionIsFalse() {
        when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
        when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
        cut.onMessageRequest(ctx).test().assertComplete();
        verify(request).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertComplete();
    }

    @Test
    void shouldNotFilterRequestMessagesOnSubExpressionWhenExpressionContainsAnExpression() {
        when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
        when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(true);
        cut.onMessageRequest(ctx).test().assertComplete();
        verify(request).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertValue(message);
    }

    @Test
    void shouldFilterRequestMessagesOnSubExpressionWhenExpressionContainsAnExpression() {
        when(ctx.request().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
        when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(false);
        cut.onMessageRequest(ctx).test().assertComplete();
        verify(request).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertComplete();
    }

    @Test
    void shouldNotFilterResponseMessagesWhenExpressionIsTrue() {
        when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), any())).thenReturn(true);
        cut.onMessageResponse(ctx).test().assertComplete();
        verify(response).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertValue(message);
    }

    @Test
    void shouldFilterResponseMessagesWhenExpressionIsFalse() {
        when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn(null);
        when(templateEngine.getValue(any(), eq(boolean.class))).thenReturn(false);
        cut.onMessageResponse(ctx).test().assertComplete();
        verify(response).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertComplete();
    }

    @Test
    void shouldNotFilterResponseMessagesOnSubExpressionWhenExpressionContainsAnExpression() {
        when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
        when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(true);
        cut.onMessageResponse(ctx).test().assertComplete();
        verify(response).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertValue(message);
    }

    @Test
    void shouldFilterResponseMessagesOnSubExpressionWhenExpressionContainsAnExpression() {
        when(ctx.response().onMessage(any())).thenReturn(Completable.complete());
        when(templateEngine.getValue(any(), eq(Object.class))).thenReturn("{#expression}");
        when(templateEngine.getValue("{#expression}", boolean.class)).thenReturn(false);
        cut.onMessageResponse(ctx).test().assertComplete();
        verify(response).onMessage(messageCaptor.capture());

        DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("content")).build();
        messageCaptor.getValue().apply(message).test().assertComplete();
    }
}
