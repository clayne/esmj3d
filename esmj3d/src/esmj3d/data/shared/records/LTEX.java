package esmj3d.data.shared.records;

import java.util.ArrayList;

import tools.io.ESMByteConvert;
import esmLoader.common.data.record.Record;
import esmLoader.common.data.record.Subrecord;
import esmj3d.data.shared.subrecords.FormID;
import esmj3d.data.shared.subrecords.ZString;

public class LTEX extends RECO
{
	public ZString EDID;

	//FO3 onwards
	public int textureSetId = -1;

	//TES4
	public ZString ICON = null;

	public byte[] HNAM = null;

	public byte[] SNAM = null;

	public FormID[] GNAMs; //grass in priority order

	public LTEX(Record recordData)
	{
		super(recordData);

		ArrayList<FormID> GNAMsl = new ArrayList<FormID>();

		ArrayList<Subrecord> subrecords = recordData.getSubrecords();
		for (int i = 0; i < subrecords.size(); i++)
		{
			Subrecord sr = subrecords.get(i);
			byte[] bs = sr.getSubrecordData();

			if (sr.getSubrecordType().equals("EDID"))
			{
				EDID = new ZString(bs);
			}
			else if (sr.getSubrecordType().equals("TNAM"))
			{
				textureSetId = ESMByteConvert.extractInt(bs, 0);// 4 bytes pointer to texture set?
			}
			else if (sr.getSubrecordType().equals("ICON"))
			{
				ICON = new ZString(bs);
			}
			else if (sr.getSubrecordType().equals("HNAM"))
			{
				HNAM = bs; //3 bytes
				//Havok Data This section allows you to choose the sound type for the selected landscape 
				//texture from the Material Type dialogue, as well as the Friction and Restitution 
			}
			else if (sr.getSubrecordType().equals("SNAM"))
			{
				SNAM = bs; //1 byte	 
				//Texture Specular Exponent, not supported
			}
			else if (sr.getSubrecordType().equals("GNAM"))
			{
				// not mandatory
				GNAMsl.add(new FormID(bs));
			}
			else if (sr.getSubrecordType().equals("MNAM"))
			{

			}
			else
			{
				System.out.println("unhandled : " + sr.getSubrecordType() + " in " + recordData);
			}

			// transfer to arrays
			GNAMs = new FormID[GNAMsl.size()];
			GNAMsl.toArray(GNAMs);
		}
	}

	public String showDetails()
	{
		return "LTEX : (" + formId + "|" + Integer.toHexString(formId) + ") " + EDID.str + " : TextureSet " + textureSetId;
	}

}
