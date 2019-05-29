package com.dingp.mvcframework.servlet;

import com.dingp.mvcframework.annotation.DPAutowried;
import com.dingp.mvcframework.annotation.DPController;
import com.dingp.mvcframework.annotation.DPRequestMapping;
import com.dingp.mvcframework.annotation.DPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * 此类为自动入口
 */
public class DpDispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    //跟web.xml中param-name的值一致
    private static final String LOCATION = "contextConfigLocation";

    //保存所有的配置信息
    private Properties p = new Properties();

    //保存所有被扫描到的相关的类名
    private List<String> classNames = new ArrayList<String>();

    //核心IOC容器
    private Map<String,Object> ioc = new HashMap<String, Object>();

    //保存所有的Url和方法的映射关系
    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    public DpDispatcherServlet(){
        super();
    }

    /**
     * 初始化，加载配置文件
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException{
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2.扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

        //3.初始化所有相关类的实例，并保存到IOC容器中
        doInstance();
        
        //4.依赖注入
        doAutowired();

        //5.构造HandlerMapping
        initHandlerMapping();

        System.out.println("dp mvcframework is init");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry:ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(DPController.class)){
                continue;
            }
            String baseUrl = "";

            //获取Controller的url配置
            if(clazz.isAnnotationPresent(DPRequestMapping.class)){
                DPRequestMapping mapping = clazz.getAnnotation(DPRequestMapping.class);
                baseUrl = mapping.value();
            }

            //获取Method的url配置
            Method[] methods = clazz.getMethods();
            for (Method method:methods) {
                if(!method.isAnnotationPresent(DPRequestMapping.class)){
                    continue;
                }
                DPRequestMapping mapping = method.getAnnotation(DPRequestMapping.class);
                String url = ("/" + baseUrl + "/" + mapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url,method);
                System.out.println("mapped"+url+","+method);
            }
        }
    }

    /**
     * 将初始化到IOC容器中的类，需要赋值的字段进行赋值
     */
    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry:ioc.entrySet()) {
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields) {
                if(!field.isAnnotationPresent(DPAutowried.class)){
                    continue;
                }
                DPAutowried autowried = field.getAnnotation(DPAutowried.class);
                String beanName = autowried.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private String lowerFirstCase(String str){
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 初始化所有相关的类，并放入到IOC容器之中
     */
    private void doInstance() {
        if (classNames.size()==0){
            return;
        }
        try{
            for (String className:classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(DPController.class)){
                    //默认将首字母小写作为beanName
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(DPService.class)){
                    DPService service = clazz.getAnnotation(DPService.class);
                    String beanName = service.value();
                    //如果用户自己设置了名字，就用用户自己设置的
                    if(!"".equals(beanName.trim())){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }
                    //如果没有，按照接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void doScanner(String packageName) {
        //将所有的包路径转化为文件路径
        String path = "/" + packageName.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource(path);
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            //如果是文件夹，继续递归
            if (file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else{
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }
    }

    private void doLoadConfig(String location) {
        try (InputStream fis = this.getClass().getClassLoader().getResourceAsStream(location)) {
            p.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doPost(request,response);
    }

    /**
     * 执行业务处理
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try{
            doDispatch(request,response);
        }catch (Exception e){
            response.getWriter().write("500 Exception,Details:\r\n"+Arrays.toString(e.getStackTrace())
                    .replaceAll("\\[|\\]","").replaceAll(",\\s","\r\n")
            );

        }

    }

    private void doDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if(this.handlerMapping.isEmpty()){
            return;
        }
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        if(!this.handlerMapping.containsKey(url)){
            response.getWriter().write("404 Not Found!");
            return;
        }

        Method method = this.handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取请求的参数
        Map<String,String[]> parameterMap = request.getParameterMap();
        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];
        //方法的参数列表
        for (int i = 0 ;i<parameterTypes.length;i++){
            //根据参数名称，做某些处理
            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class){
                paramValues[i]=request;
                continue;
            }else if (parameterType == HttpServletResponse.class){
                paramValues[i]=response;
                continue;
            }else if(parameterType == String.class){
                for (Map.Entry<String,String[]> param: parameterMap.entrySet()) {
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
        }
        try {
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            method.invoke(this.ioc.get(beanName),paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


}
