package com.damon.Thread;

import java.util.LinkedList;

public class LinkedListDemo {
    public static void main(String[] args){
        LinkedList link = new LinkedList();

        link.add("hello");
		link.add("world");
		link.add("java");

        link.addFirst("damon");
        link.addLast("chen");

        System.out.println(link);
        System.out.println(link.getFirst());
        System.out.println(link.removeLast());
    }
}
