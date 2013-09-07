package esmj3d.data.shared.subrecords;

import tools.io.ESMByteConvert;
import tools.io.PrimitiveBytes;

public class FormID
{
	public int formId;

	public FormID(byte[] bytes)
	{
		formId = ESMByteConvert.extractInt(bytes, 0);
		//if (formId < -1 || formId > 5000000)
		//{
		//	new Throwable("Odd formId? " + formId).printStackTrace();
		//}
	}

	public byte[] getBytes()
	{
		byte[] bytes = new byte[4];
		PrimitiveBytes.insertInt(bytes, formId, 0);
		return bytes;
	}
}
