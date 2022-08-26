package com.damon.Thread;

import java.util.HashSet;
import java.util.Set;

public class HashSetDemo {
    public static void main(String[] args){
        HashSet<Person> set = new HashSet<Person>();

        set.add("aaa");
        set.add("bbb");
    
        for (String s : set) {
            System.out.println(s);
        }
    }
}
