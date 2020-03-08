package com.lagou.edu.factory;

import com.lagou.edu.annotation.MyAutowired;
import com.lagou.edu.annotation.MyService;
import com.lagou.edu.annotation.MyTransactional;
import com.lagou.edu.utils.ClassScanUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author 应癫
 *
 * 工厂类，生产对象（使用反射技术）
 */
public class BeanFactory {

    /**
     * 任务一：读取解析xml，通过反射技术实例化对象并且存储待用（map集合）
     * 任务二：对外提供获取实例对象的接口（根据id获取）
     */

    private static Map<String,Object> map = new HashMap<>();  // 存储对象

    static {
        // 任务一：读取解析xml，通过反射技术实例化对象并且存储待用（map集合）
        // 加载xml
        InputStream resourceAsStream = BeanFactory.class.getClassLoader().getResourceAsStream("beans.xml");
        // 解析xml
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(resourceAsStream);
            Element rootElement = document.getRootElement();
            List<Element> beanList = rootElement.selectNodes("//bean");
            for (int i = 0; i < beanList.size(); i++) {
                Element element =  beanList.get(i);
                // 处理每个bean元素，获取到该元素的id 和 class 属性
                String id = element.attributeValue("id");        // accountDao
                String clazz = element.attributeValue("class");  // com.lagou.edu.dao.impl.JdbcAccountDaoImpl
                // 通过反射技术实例化对象
                Class<?> aClass = Class.forName(clazz);
                Object o = aClass.newInstance();  // 实例化之后的对象

                // 存储到map中待用
                map.put(id,o);

            }

            // 实例化完成之后维护对象的依赖关系，检查哪些对象需要传值进入，根据它的配置，我们传入相应的值
            // 有property子元素的bean就有传值需求
            List<Element> propertyList = rootElement.selectNodes("//property");
            // 解析property，获取父元素
            for (int i = 0; i < propertyList.size(); i++) {
                Element element =  propertyList.get(i);   //<property name="AccountDao" ref="accountDao"></property>
                String name = element.attributeValue("name");
                String ref = element.attributeValue("ref");

                // 找到当前需要被处理依赖关系的bean
                Element parent = element.getParent();

                // 调用父元素对象的反射功能
                String parentId = parent.attributeValue("id");
                Object parentObject = map.get(parentId);
                // 遍历父对象中的所有方法，找到"set" + name
                Method[] methods = parentObject.getClass().getMethods();
                for (int j = 0; j < methods.length; j++) {
                    Method method = methods[j];
                    if(method.getName().equalsIgnoreCase("set" + name)) {  // 该方法就是 setAccountDao(AccountDao accountDao)
                        method.invoke(parentObject,map.get(ref));
                    }
                }

                // 把处理之后的parentObject重新放到map中
                map.put(parentId,parentObject);

            }


        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    static {
        //获取需要扫描注解的包
        InputStream resourceAsStream = BeanFactory.class.getClassLoader().getResourceAsStream("beans.xml");
        SAXReader saxReader = new SAXReader();
        List<String> packages = new ArrayList<>();
        try {
            Document document = saxReader.read(resourceAsStream);
            Element rootElement = document.getRootElement();
            List<Element> selectNodes = rootElement.selectNodes("//scan");
            for (Element element : selectNodes) {
                String value = element.attributeValue("value");
                packages.add(value);
            }
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        //扫描包内的注解
        for (String packagePath : packages) {
            //MyService的注解
            Class<MyService> myServiceClass = MyService.class;
            Set<Class> myServiceClasses = ClassScanUtil.getClasses(packagePath, myServiceClass);
            for (Class aClass : myServiceClasses) {
                try {
                    String className = aClass.getSimpleName();
                    //id的设置，是否有value值
                    MyService myServiceAnnotation = (MyService) aClass.getAnnotation(myServiceClass);
                    String value = myServiceAnnotation.value();
                    String id = value;
                    if (value.equals("")) {
                        id = getToLowerId(className);
                    }
                    Object o = null;
                    if (map.get(id) != null) {
                        o = map.get(id);
                    }else {
                        o = aClass.newInstance();

                    }
                    //MyAutowired的注解
                    Field[] declaredField = aClass.getDeclaredFields();
                    for (Field field : declaredField) {
                        MyAutowired myAutowiredAnnotation = field.getAnnotation(MyAutowired.class);
                        if (myAutowiredAnnotation != null) {
                            field.setAccessible(true);
                            String typeClassName = field.getType().getSimpleName();
                            String typeId = getToLowerId(typeClassName);
                            field.set(o ,map.get(typeId));
                        }
                    }
                    map.put(id, o);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //获取@Transactional的注解类
            Set<Class> transactionalClasses = ClassScanUtil.getClasses(packagePath, MyTransactional.class);
            for (Class transactionalClass : transactionalClasses) {
                String classSimpleName = transactionalClass.getSimpleName();
                String toLowerId = getToLowerId(classSimpleName);
                Object o = null;
                if (map.get(toLowerId) == null) {
                    try {
                        o = transactionalClass.newInstance();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }else {
                    o = map.get(toLowerId);
                }
                ProxyFactory proxyFactory = (ProxyFactory) map.get("proxyFactory");
                Object jdkProxy = proxyFactory.getJdkProxy(o);
                map.put(toLowerId, jdkProxy);
            }

        }
    }

    private static String getToLowerId(String str) {
        return new StringBuilder().append(Character.toLowerCase(str.charAt(0))).append(str.substring(1)).toString();
    }


    // 任务二：对外提供获取实例对象的接口（根据id获取）
    public static  Object getBean(String id) {
        return map.get(id);
    }

}
