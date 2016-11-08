package com.zimmerbell.repaper;

import java.io.IOException;
import java.io.Serializable;

public interface Source {
	
	public void update() throws IOException;

	public String getImageUri() throws IOException;

	public String getTitle() throws IOException;

	public String getBy() throws IOException;
}
