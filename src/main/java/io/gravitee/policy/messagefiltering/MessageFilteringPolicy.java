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

import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.messagefiltering.configuration.MessageFilteringPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.AllArgsConstructor;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@AllArgsConstructor
public class MessageFilteringPolicy implements Policy {

    private final MessageFilteringPolicyConfiguration configuration;

    @Override
    public String id() {
        return "message-filtering";
    }

    @Override
    public Completable onMessageRequest(MessageExecutionContext ctx) {
        return Completable.defer(() -> {
            String computedFilter = computeFilter(ctx);
            return ctx.request().onMessage(message -> filter(ctx, computedFilter, message));
        });
    }

    @Override
    public Completable onMessageResponse(MessageExecutionContext ctx) {
        return Completable.defer(() -> {
            String computedFilter = computeFilter(ctx);
            return ctx.response().onMessage(message -> filter(ctx, computedFilter, message));
        });
    }

    private String computeFilter(final MessageExecutionContext ctx) {
        String computedFilter = configuration.getFilter();
        try {
            Object firstEvaluation = ctx.getTemplateEngine().getValue(computedFilter, Object.class);
            // If the return evaluation is a String, that means the configuration filter contains an EL referencing an EL as String, so double evaluation is required.
            if (firstEvaluation instanceof String) {
                computedFilter = (String) firstEvaluation;
            }
        } catch (Exception ex) {
            // Ignore evaluation exception
        }
        return computedFilter;
    }

    private Maybe<Message> filter(final MessageExecutionContext ctx, final String computedFilter, final Message message) {
        try {
            boolean filtered = ctx.getTemplateEngine(message).getValue(computedFilter, boolean.class);

            return filtered ? Maybe.just(message) : Maybe.empty();
        } catch (Exception ex) {
            return Maybe.empty();
        }
    }
}
