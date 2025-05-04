package com.joe.task.dynamicquery;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface DynamicQuery
{
	 void save(Object entity);

	 void update(Object entity);

	 <T> void delete(Class<T> entityClass, Object entityid);

	 <T> void delete(Class<T> entityClass, Object[] entityids);
	
	/**
	 * 执行nativeSql统计查询
	 * @param nativeSql
	 * @param params 占位符参数(例如?1)绑定的参数值
	 * @return 统计条数
	 */
	Long nativeQueryCount(String nativeSql, Object... params);

    /**
     * 执行nativeSql分页查询
     * @param resultClass
     * @param pageable
     * @param nativeSql
     * @param params
     * @param <T>
     * @return
     */
    <T> List<T> nativeQueryPagingList(Class<T> resultClass, Pageable pageable, String nativeSql, Object... params);

	/**
	 * 查询统计的时候使用
	 * @param nativeSql
	 * @param keyName
	 * @param valName
	 * @return
	 */
	Map<String, Long> nativeCustomQuery(String nativeSql, String keyName, String valName);
}
