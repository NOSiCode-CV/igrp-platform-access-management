package cv.igrp.platform.access_management.shared.infrastructure.spring;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.CommandHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
@Component
public class SpringCommandBus implements CommandBus {

   private final Map<Class<? extends Command>, CommandHandler<?, ?>> handlers = new HashMap<>();

   public SpringCommandBus(List<CommandHandler<?, ?>> handlerList) {
      for (CommandHandler<?, ?> handler : handlerList) {
         Class<?> commandType = (Class<?>) ((ParameterizedType) handler.getClass()
                 .getGenericInterfaces()[0]).getActualTypeArguments()[0];
         handlers.put((Class<? extends Command>) commandType, handler);
      }
   }

   @Override
   public <C extends Command, R> R send(C command) {
      CommandHandler<C, R> handler = (CommandHandler<C, R>) handlers.get(command.getClass());
      if (handler == null) {
         throw new IllegalArgumentException("No handler found for command type: " + command.getClass());
      }
      return handler.handle(command);
   }
}