package org.love.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.love.ProxyFactory.ConnProxyFactory;
import org.love.dbutil.DbUtils;

public class SimpleDataSource implements DataSource {

	// 最小可用连接数
	private int minIdle = 5;

	// 初始化连接数
	private int initSize = 20;

	// 同时连接最大并发数量
	private int maxActive = 8;

	private String driverClassName = "com.mysql.jdbc.Driver";
	private String username = "root";
	private String password = "root";
	private String url = "jdbc:mysql://localhost:3306/test?useUnicode=true&amp;characterEncoding=utf8";

	// 连接代理工厂
	private ConnProxyFactory cpf = new ConnProxyFactory(this);

	protected final ReentrantLock lock = new ReentrantLock(true);

	// 连接池
	private List<Connection> connPool = new ArrayList<Connection>();

	// 连接不成功次数
	private AtomicInteger errcount = new AtomicInteger(0);

	// 应用中的连接并发数量
	private int activeCount = 0;

	public SimpleDataSource() {
		System.out.println("Hello I am SimpleDataSource !");
		init();
	}

	public void init() {
		DbUtils.loadDriver(driverClassName);
		lock.lock();
		try{
			for (int i = 0; i < initSize; i++) {
				connPool.add(createConnection());
			}	
		}finally{
			lock.unlock();
		}
		
	}

	// 释放连接
	public void closeConnection(Connection conn) throws SQLException {
		// 假如释放的是一个关闭的连接，则在连接池中删除这个连接
		//System.out.println(Thread.currentThread().getName()+"等待释放");
		lock.lock();
		try {
			putPool(conn);
			activeCount--;
			//System.out.println(Thread.currentThread().getName()+"正在释放");
			/*System.out.println("关闭连接,剩余空闲数: " + connPool.size() + "并发数: "
					+ activeCount);*/
		} finally {
			lock.unlock();
		}
		
		
	}

	/**
	 * 此方法必须保证还有可用连接 外围方法必须同步
	 * 
	 * @return
	 */
	private Connection searchConntion() {
		Connection conn = connPool.get(0);
		connPool.remove(0);
		return conn;
	}

	/**
	 * 放入连接池
	 */
	private void putPool(Connection conn) {
		connPool.add(conn);
	}

	/**
	 * 创建新的连接 外围方法必须同步
	 * 
	 * @return
	 * @throws SQLException
	 */
	private Connection createConnection() {
		Connection conn = null;
		try {
			conn = (Connection) cpf.factory(DriverManager.getConnection(url,
					username, password), null);
		} catch (SQLException e) {
			errcount.incrementAndGet();
			System.out.println("连接错误次数: " + errcount);
			if (errcount.get() >= 5) {
				throw new RuntimeException(" cannot connect the database!");
			}
			try {
				Thread.sleep(5000);
				conn = createConnection();
				if (conn != null) {
					errcount.set(0);
					return conn;
				}

			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

		}
		return conn;

	}

	public Connection getConnection() throws SQLException {
		Connection conn = null;
		lock.lock();
		try {
			if (connPool.size() <= minIdle) {
				//System.out.println("生成连接==========================");
				conn = createConnection();
			} else {
				conn = searchConntion();
			}
			activeCount++;
			//System.out.println("剩余空闲数: " + connPool.size() + "并发数: "
				//	+ activeCount);
		} finally {
			lock.unlock();
		}

		return conn;

	}


	public Connection getConnection(String username, String password)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public PrintWriter getLogWriter() throws SQLException {
		throw new UnsupportedOperationException("getLogWriter");
	}

	public int getLoginTimeout() throws SQLException {
		return 0;
	}

	public void setLogWriter(PrintWriter out) throws SQLException {
		throw new UnsupportedOperationException("setLogWriter");
	}

	public void setLoginTimeout(int seconds) throws SQLException {
		throw new UnsupportedOperationException("setLoginTimeout");
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return DataSource.class.equals(iface);
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return (T) this;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
