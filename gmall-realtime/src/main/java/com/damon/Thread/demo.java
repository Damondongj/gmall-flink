package com.damon.Thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class demo {
    public static void main(String[] args){
        // // 创建集合对象
		// Collection c = new ArrayList();

		// // 往集合中添加元素
		// // String s = "hello";
		// c.add("hello");
		// c.add("world");
		// c.add("java");

		// // Object[] toArray():把集合转成对象数组
		// Object[] objs = c.toArray();
		// // 遍历数组
		// for (int x = 0; x < objs.length; x++) {
		// 	// System.out.println(objs[x]);
		// 	String s = (String) objs[x];
		// 	System.out.println(s);
		// }

        // 创建集合对象
		Collection c = new ArrayList();

		// 创建元素并添加元素
		c.add("hello");
		c.add("world");
		c.add("java");

		// 集合中的方法：Iterator iterator()
		Iterator it = c.iterator();// 右边其实是接口的实现类对象，这是多态的应用

		// System.out.println(it.next());
		// System.out.println(it.next());
		// System.out.println(it.next());
		// System.out.println(it.next());
		// 代码一样，用循环实现
		while (it.hasNext()) {
			System.out.println((String) it.next());
		}
    }
    
}
