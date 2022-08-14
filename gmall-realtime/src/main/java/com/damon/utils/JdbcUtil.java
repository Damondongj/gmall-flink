package com.damon.utils;

import com.alibaba.fastjson.JSONObject;
import com.damon.common.GmallConfig;
import com.google.common.base.CaseFormat;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcUtil {

    /**
     *
     * @param connection 与数据库连接
     * @param querySql 查询语句
     * @param clz 不懂？？
     * @param underScoreToCamel true/false
     * @param <T> 不懂
     * @return ArrayList columnName value
     * @throws Exception 异常
     */
    public static <T> List<T> queryList(Connection connection, String querySql, Class<T> clz, boolean underScoreToCamel) throws Exception {

        // 创建集合用于存放查询结果数据
        ArrayList<T> resultList = new ArrayList<>();

        // 预编译SQL
        PreparedStatement preparedStatement = connection.prepareStatement(querySql);

        // 解析resultSet
        ResultSet resultSet = preparedStatement.executeQuery();

        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (resultSet.next()) {

            // 创建泛型对
            T t = clz.newInstance();

            // 给泛型对象赋值
            for (int i = 0; i < columnCount + 1; i++) {

                // 获取列名
                String columnName = metaData.getColumnName(i);

                // 判断是否需要转换为驼峰命名
                if (underScoreToCamel) {
                    columnName = CaseFormat.LOWER_UNDERSCORE
                            .to(CaseFormat.LOWER_CAMEL, columnName.toLowerCase());
                }

                // 获取列值
                Object value = resultSet.getObject(i);

                // 给泛型对象赋值
                BeanUtils.setProperty(t, columnName, value);
            }
            resultList.add(t);
        }
        preparedStatement.close();
        resultSet.close();
        return resultList;
    }

    public static void main(String[] args) throws Exception{
        Class.forName(GmallConfig.PHOENIX_DRIVER);
        Connection connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);

        List<JSONObject> queryList = queryList(connection,
                "select * from GMALL210325_REALTIME.DIM_USER_INFO",
                JSONObject.class,
                true);
        for (JSONObject jsonObject: queryList) {
            System.out.println(jsonObject);
        }
        connection.close();
    }
}
