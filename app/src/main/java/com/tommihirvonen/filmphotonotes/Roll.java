package com.tommihirvonen.filmphotonotes;

// Copyright 2015
// Tommi Hirvonen

public class Roll {

    public int id;
    public String name;
    public String date;
    public String note;

    public Roll(){

    }

    public Roll(int id, String name, String date, String note){
        this.id = id;
        this.name = name;
        this.date = date;
        this.note = note;
    }

    public void setId(int input) {
        this.id = input;
    }

    public void setName(String input){
        this.name = input;
    }

    public void setDate(String input) {
        this.date = input;
    }

    public void setNote(String input) {
        this.note = input;
    }

    public int getId(){
        return this.id;
    }

    public String getName(){
        return this.name;
    }

    public String getDate(){
        return this.date;
    }

    public String getNote(){
        return this.note;
    }

}
