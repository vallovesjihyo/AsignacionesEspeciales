package com.example.myapp.data;

import java.util.ArrayList;

public class MyData {
    public ArrayList<Integer> update_id;
    public ArrayList<String> msg;

    public MyData(){
        initialize();
    }

    public void initialize(){
        update_id = new ArrayList<Integer>();
        msg = new ArrayList<String>();
    }
}
