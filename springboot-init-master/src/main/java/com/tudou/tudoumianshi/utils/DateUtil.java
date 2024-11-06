package com.tudou.tudoumianshi.utils;


import java.util.Date;

public class DateUtil {

   /**
    * 获取当前时间与指定时间的差值，单位为秒。
    *
    * @param date 指定的时间
    * @return 时间差（秒）
    */
   public static long secondsBetween(Date date1, Date date2) {
       long diff = date2.getTime() - date1.getTime();
       return diff / 1000;
   }
}