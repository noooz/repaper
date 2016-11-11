package com.zimmerbell.repaper;

public interface Source {
	
	public void update() throws Exception;

	public String getImageUri() throws Exception;

	public String getTitle() throws Exception;

	public String getBy() throws Exception;

	public String getDetailsUri() throws Exception;
}
