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
import akka.http.scaladsl.model.HttpResponse;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

public class AkkaHttpBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bindFactory(AkkaHttpRequestReferencingFactory.class).to(HttpRequest.class).proxy(true)
                .proxyForSameScope(false).in(RequestScoped.class);
        bindFactory(ReferencingFactory.<HttpRequest>referenceFactory())
                .to(new TypeLiteral<Ref<HttpRequest>>() {}).in(RequestScoped.class);

        bindFactory(AkkaHttpResponseReferencingFactory.class).to(HttpResponse.class).proxy(true)
                .proxyForSameScope(false).in(RequestScoped.class);
        bindFactory(ReferencingFactory.<HttpResponse>referenceFactory())
                .to(new TypeLiteral<Ref<HttpResponse>>() {}).in(RequestScoped.class);

        bindFactory(AkkaHttpRequestReferencingFactoryServlet.class).to(HttpServletRequest.class);
    }


    private static class AkkaHttpRequestReferencingFactory extends ReferencingFactory<HttpRequest> {

        @Inject
        public AkkaHttpRequestReferencingFactory(final Provider<Ref<HttpRequest>> referenceFactory) {
            super(referenceFactory);
        }
    }

    private static class AkkaHttpResponseReferencingFactory extends ReferencingFactory<HttpResponse> {

        @Inject
        public AkkaHttpResponseReferencingFactory(final Provider<Ref<HttpResponse>> referenceFactory) {
            super(referenceFactory);
        }
    }

    private static class AkkaHttpRequestReferencingFactoryServlet implements Factory<HttpServletRequest> {

        private HttpRequest request;

        @Inject
        public AkkaHttpRequestReferencingFactoryServlet(HttpRequest request) {
            this.request = request;
        }

        @Override
        public HttpServletRequest provide() {
            return new Request2ServletWrapper(this.request);
        }

        @Override
        public void dispose(HttpServletRequest httpServletRequest) {

        }
    }
}
