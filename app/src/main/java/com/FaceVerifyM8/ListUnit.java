package com.FaceVerifyM8;

public class ListUnit {
	private long id = 0;
	private String workno = "";
	private String name = "";
	private String time = "";
	private String photo = null;
	private String picture = null;
	private int score = 0;
	private int status = 0;

	public long getId() { return id; }
	public void setId(long lID) { id = lID; }
	public String getWorkNo() { return workno; }
	public void setWorkNo(String strWorkNo) { workno = strWorkNo; }
	public String getName() {
		return name;
	}
	public void setName(String strName) { if(strName != null) name = strName; else name = ""; }
	public String getTime() {
		return time;
	}
	public void setTime(String strTime) { if(strTime != null) time = strTime; else time = ""; }
	public String getPhoto() { if(photo != null && !photo.isEmpty()) return photo; else return null; }
	public void setPhoto(String strPhoto) { photo = strPhoto; }
	public String getPicture() { if(picture != null && !picture.isEmpty()) return picture; else return null; }
	public void setPicture(String strPicture) { picture = strPicture; }
	public int getScore() { return score; }
	public void setScore(int iScore) { score = iScore; }
	public int getStatus() { return status; }
	public void setStatus(int iStatus) { status = iStatus; }
}
