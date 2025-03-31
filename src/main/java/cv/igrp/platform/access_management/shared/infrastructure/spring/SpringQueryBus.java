package cv.igrp.platform.access_management.shared.infrastructure.spring;

import cv.igrp.framework.core.domain.Query;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.core.domain.QueryHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
@Component
public class SpringQueryBus implements QueryBus {

   private final Map<Class<? extends Query>, QueryHandler<?, ?>> handlers = new HashMap<>();

   public SpringQueryBus(List<QueryHandler<?, ?>> handlerList) {
      for (QueryHandler<?, ?> handler : handlerList) {
         Class<?> QueryType = (Class<?>) ((ParameterizedType) handler.getClass()
                 .getGenericInterfaces()[0]).getActualTypeArguments()[0];
         handlers.put((Class<? extends Query>) QueryType, handler);
      }
   }

   @Override
   public <Q extends Query, R> R handle(Q query) {
      QueryHandler<Q, R> handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
      if (handler == null) {
         throw new IllegalArgumentException("No handler found for query type: " + query.getClass());
      }
      return handler.handle(query);
   }
}