package com.db;

public class UserBean {
	public long uid = 0; // user ID
	public String uname = ""; // user name
	public String workno = ""; // work no
	public String card = ""; // card no
	public String photoFileName = ""; // photo filename
	public byte[] feature = null; // feature
	public int featurePos = -1; // feature pos to remove (not save to Datebase)

	public UserBean() { super(); }
}
