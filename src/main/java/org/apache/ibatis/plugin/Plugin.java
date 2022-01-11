/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  private final Object target;
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 将某个拦截器与四大对象相关联
   * @param target Mybatis中的四大对象
   * @param interceptor 拦截器链路中的某个拦截器
   * @return
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    // 获取拦截器@Interceptor{@Signature()}注解中配置的要拦截的类及方法
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    Class<?> type = target.getClass();
    // 查看当前对象实现了signatureMap中哪些类
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    if (interfaces.length > 0) {
      // 如果需要拦截当前对象，则返回动态代理对象，动态代理对象在执行所有方法时，都会走Plugin的invoke方法，
      // 通过判断执行的方法是否在signatureMap中，从而实现特定类的特定方法拦截
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));
    }
    // 如果当前拦截器不需要拦截当前对象，则直接返回对象，不返回动态代理对象
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {
        return interceptor.intercept(new Invocation(target, method, args));
      }
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  /**
   * 查看四大对象实现的接口中，是否具有signatureMap想要拦截的类，并返回该类
   * 这一步主要是在去除无用的信息，比如用户填写了我想要拦截ResultSettttHandler，
   * 但是四大对象中根本没有人实现这个类，主要就是去除这些无用的信息
   * @param type Mybatis中四大对象的类
   * @param signatureMap 注解Interceptor{@Signature()}中配置的想要拦截的类及方法
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type,
                                             Map<Class<?>, Set<Method>> signatureMap) {
    // 最后的结果集
    Set<Class<?>> interfaces = new HashSet<>();
    // 获取当前类及其父类，直到继承关系的顶层
    while (type != null) {
      // 获取当前类实现的接口
      for (Class<?> c : type.getInterfaces()) {
        // 如果希望被拦截的接口中，当前对象实现了该接口，那么就将其加入结果中
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 获取超类，返回循环头
      type = type.getSuperclass();
    }
    // 因Set具有不重复的特点，用其去重，现转换为数组。
    return interfaces.toArray(new Class<?>[0]);
  }

}
