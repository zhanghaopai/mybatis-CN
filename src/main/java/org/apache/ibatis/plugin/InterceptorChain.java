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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拦截器链路
 *
 * @author Clinton Begin
 */
public class InterceptorChain {

  /**
   * 链路，责任链模式
   */
  private final List<Interceptor> interceptors = new ArrayList<>();

  /**
   * @param target 需要被拦截的对象，Mybatis中的四大对象Executor、ParameterHandler、ResultSetHandler、StatementHandler
   * @return
   */
  public Object pluginAll(Object target) {
    // 为Mybatis的四大对象添加插件
    // 每种对象被初始化的时候，都需要为该对象设置动态代理
    // 这样该对象的任何方法在执行前都会走到动态代理中，从而实现拦截
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  /**
   * 向链路中添加拦截器
   *
   * @param interceptor 新增的拦截器对象
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  /**
   * 获取系统内所有的拦截器
   *
   * @return 系统内所有的拦截器
   */
  public List<Interceptor> getInterceptors() {
    // 返回不可修改对象，防止篡改对象，仅能通过addInterceptor()方法向链路中添加对象
    return Collections.unmodifiableList(interceptors);
  }

}
