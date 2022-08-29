package com.damon.Thread;

import java.util.ArrayList;
import java.util.Iterator;


// ArrayList 底层数据结构是数组，查询快，增删慢 线程不安全，效率高
public class ArrayListDemo {
    
    public static void main(String[] args){
        ArrayList array = new ArrayList();

        array.add("hello");
        array.add("world");
        array.add("python");

        // Iterator it = array.iterator();
        // while(it.hasNext()) {
        //     String s = (String) it.next();
        //     System.out.println(s);
        // }
        System.out.println("--------------------");

        for (int x = 0; x < array.size(); x++) {
            String s  = (String)array.get(x);
            System.out.println(s);
        }
    }
}
