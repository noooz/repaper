package com.zimmerbell.repaper;

import java.io.IOException;
import java.io.Serializable;

public interface Source extends Serializable {

	public String getImageUri() throws IOException;
}
