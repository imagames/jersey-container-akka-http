/*
* Copyright 2017 Imagames Gamification Services S.L.
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
package com.imagames.jersey.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.inject.Inject;
import javax.inject.Provider;

public class AkkaHttpBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bindFactory(AkkaHttpRequestReferencingFactory.class).to(HttpRequest.class).proxy(true)
                .proxyForSameScope(false).in(RequestScoped.class);
        bindFactory(ReferencingFactory.<HttpRequest>referenceFactory())
                .to(new TypeLiteral<Ref<HttpRequest>>() {}).in(RequestScoped.class);
    }


    private static class AkkaHttpRequestReferencingFactory extends ReferencingFactory<HttpRequest> {

        @Inject
        public AkkaHttpRequestReferencingFactory(final Provider<Ref<HttpRequest>> referenceFactory) {
            super(referenceFactory);
        }
    }
}
