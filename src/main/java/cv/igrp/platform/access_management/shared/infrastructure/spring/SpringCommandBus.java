package cv.igrp.platform.access_management.shared.infrastructure.spring;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.CommandHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
@Component
public class SpringCommandBus implements CommandBus {

   private final Map<Class<? extends Command>, CommandHandler<?, ?>> handlers = new HashMap<>();

   public SpringCommandBus(List<CommandHandler<?, ?>> handlerList) {
      for (CommandHandler<?, ?> handler : handlerList) {
         Class<?> targetClass = resolveTargetClass(handler); // use the method above
         Type[] interfaces = targetClass.getGenericInterfaces();

         for (Type iface : interfaces) {
            if (iface instanceof ParameterizedType) {
               ParameterizedType pt = (ParameterizedType) iface;
               Type actualType = pt.getActualTypeArguments()[0];
               if (actualType instanceof Class<?>) {
                  Class<?> commandType = (Class<?>) actualType;
                  handlers.put((Class<? extends Command>) commandType, handler);
               }
            }
         }
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

   private Class<?> resolveTargetClass(Object bean) {
      Class<?> clazz = bean.getClass();

      // Check for CGLIB-generated class name
      while (clazz.getName().contains("$$")) {
         clazz = clazz.getSuperclass();
      }

      return clazz;
   }

}
