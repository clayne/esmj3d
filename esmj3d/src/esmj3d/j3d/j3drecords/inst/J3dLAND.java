package esmj3d.j3d.j3drecords.inst;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.jogamp.java3d.GLSLShaderProgram;
import org.jogamp.java3d.GeometryArray;
import org.jogamp.java3d.Group;
import org.jogamp.java3d.IndexedGeometryArray;
import org.jogamp.java3d.IndexedTriangleArray;
import org.jogamp.java3d.IndexedTriangleStripArray;
import org.jogamp.java3d.J3DBuffer;
import org.jogamp.java3d.Material;
import org.jogamp.java3d.RenderingAttributes;
import org.jogamp.java3d.Shader;
import org.jogamp.java3d.ShaderAppearance;
import org.jogamp.java3d.ShaderAttributeSet;
import org.jogamp.java3d.ShaderAttributeValue;
import org.jogamp.java3d.ShaderProgram;
import org.jogamp.java3d.Shape3D;
import org.jogamp.java3d.SourceCodeShader;
import org.jogamp.java3d.TextureUnitState;
import org.jogamp.java3d.geom.GeometryData;
import org.jogamp.vecmath.Color4f;
import org.jogamp.vecmath.TexCoord2f;
import org.jogamp.vecmath.Vector3f;

import com.frostwire.util.SparseArray;

import esfilemanager.common.data.record.IRecordStore;
import esfilemanager.common.data.record.Record;
import esfilemanager.tes3.IRecordStoreTes3;
import esmj3d.data.shared.records.LAND;
import esmj3d.data.shared.records.LAND.ATXT;
import esmj3d.data.shared.records.LAND.BTXT;
import esmj3d.data.shared.records.LAND.VTXT;
import esmj3d.data.shared.records.LTEX;
import esmj3d.data.shared.records.TXST;
import esmj3d.j3d.TESLANDGen;
import nif.BgsmSource;
import nif.j3d.J3dNiGeometry;
import nif.j3d.J3dNiTriBasedGeom;
import nif.niobject.bgsm.BSMaterial;
import nif.niobject.bgsm.ShaderMaterial;
import nif.shader.ShaderSourceIO;
import tools.io.ESMByteConvert;
import tools3d.utils.PhysAppearance;
import tools3d.utils.Utils3D;
import utils.ESConfig;
import utils.source.TextureSource;

public class J3dLAND extends J3dRECOStatInst
{
	public static int GRID_COUNT = 32;

	public static final float TERRIAN_SQUARE_SIZE = 128 * ESConfig.ES_TO_METERS_SCALE;// = 1.6256m

	protected static final boolean OUTPUT_BINDINGS = false;

	public static float TEX_REPEAT = 0.5f;// suggests how many times to repeat the texture over a grid square

	//	NOTE nif x,y,z to j3d x,z,-y
	public static float HEIGHT_TO_J3D_SCALE = ESConfig.ES_TO_METERS_SCALE * 2f;//this is one inch! 0.0254m

	public static float LAND_SIZE = GRID_COUNT * TERRIAN_SQUARE_SIZE; //= (32*1.6256) = 52.0192 (or 104.0384 tes3)

	public static boolean BY_REF = true;

	public static boolean BUFFERS = true;

	public static boolean INTERLEAVE = false;// DO NOT TURN ON until pipeline supports it

	//FIXME: can't turn on because vertex attributes need to be stripified at the same time
	// tristrips are stitched now, but the landgen thing makes a mess of getting tristrips
	public static boolean STRIPIFY = false;// DO NOT TURN ON massive increase in draw calls 

	//LOD tristrip in 5.12 increments (2.56?)
	//public static float HEIGHT_TO_J3D_SCALE = 0.057f;

	/*Oblivion uses a coordinate system with units which, like in Morrowind, are 21.3 'units' to a foot, or 7 units to 10 centimeters 	 
	 (or to put it another way 64 units per yard [~70 units per metre]).	
	 The base of this system is an exterior cell which is 4096 x 4096 units or 192 x 192 feet or 58.5 x 58.5 meters.
	 
	 Another way of approximation is that any race at height 1.0 will be 128 'units' tall, and we assume that the average height of the people of 
	 Tamriel is 6 feet. 128 divided by 6 is 21+(1/3) (twenty-one and a third). Round this down, and 21 units per foot gives an average height of 
	 about 6' 1.14". This seems to be a reasonable approximation.
	 
	 When importing height values for terrain into TESCS, 1 person height is only 64 units. Which equates to a smidge under 35 units per metre 
	 (32 units per yard). Also, the game seems to round height values down to the nearest 4 units, so this gives a vertical resolution of 5.7cm 
	 (2.25 inches). But when you load your terrain into the game, it seems to scale the height by 2 so that 64 vertical units equals 1 yard again! 
	 Confused? I certainly have been for the past couple of hours! 
	 http://cs.elderscrolls.com/constwiki/index.php/Oblivion_Units */

	/* TES3
	  37: LAND =  1390 (    28,  27374.14,  30243)
	Landscape
	INTV (8 bytes)
		long CellX
		long CellY
			The cell coordinates of the cell.
	DATA (4 bytes)
		long Unknown (default of 0x09)
			Changing this value makes the land 'disappear' in the editor.			
	VNML (12675 bytes)
		struct {
		  signed byte X
		  signed byte Y
		  signed byte Z
		} normals[65][65];
			A RGB color map 65x65 pixels in size representing the land normal vectors.
			The signed value of the 'color' represents the vector's component. Blue
			is vertical (Z), Red the X direction and Green the Y direction. Note that
			the y-direction of the data is from the bottom up.
	VHGT (4232 bytes)
		float Unknown1
			A height offset for the entire cell. Decreasing this value will shift the
			entire cell land down.
		byte Unknown2 (0x00)
		signed byte  HeightData[65][65]
			Contains the height data for the cell in the form of a 65x65 pixel array. The
			height data is not absolute values but uses differences between adjacent pixels.
			Thus a pixel value of 0 means it has the same height as the last pixel. Note that
			the y-direction of the data is from the bottom up.
		short Unknown2 (0x0000)
	WNAM (81 bytes)
		byte Data[9][9]
			Unknown byte data.		
	VCLR (12675 bytes) optional
		Vertex color array, looks like another RBG image 65x65 pixels in size.
	VTEX (512 bytes) optional
		A 16x16 array of short texture indices (from a LTEX record I think).
		*/

	public static void setTes3()
	{
		GRID_COUNT = 64;//64 not 32
		LAND_SIZE = GRID_COUNT * TERRIAN_SQUARE_SIZE;//refresh
		TEX_REPEAT = 0.25f;
	}

	//private GeometryInfo gi;//for Bullet later

	private LAND land;

	// just for subclass LANDFar to use
	public J3dLAND(LAND land, boolean enableSimpleFade, boolean makePhys)
	{
		super(land, enableSimpleFade, makePhys);
	}
	/**
	 * Makes the physics version of land
	 */
	public J3dLAND(LAND land)
	{
		super(land, false, false);
		this.land = land;
		if (land.VHGT != null)
		{
			// extract the heights
			byte[] heightBytes = land.VHGT;
			float[][] heights = extractHeights(heightBytes);

			//now translate the heights into a nice mesh, 82 has been confirmed empirically			
			//Note that 33 by 33 sets of point equals 32 by 32 sets of triangles between them
			TESLANDGen gridGenerator = new TESLANDGen(J3dLAND.LAND_SIZE, J3dLAND.LAND_SIZE, (GRID_COUNT + 1), (GRID_COUNT + 1), heights,
					null, null, null);
			GeometryData terrainData = new GeometryData();
			gridGenerator.generateIndexedTriangleStrips(terrainData);

			Shape3D shape = new Shape3D();
			IndexedTriangleStripArray physicsTriStripArray = new IndexedTriangleStripArray(terrainData.vertexCount,
					GeometryArray.COORDINATES | GeometryArray.USE_NIO_BUFFER | GeometryArray.BY_REFERENCE
							| GeometryArray.BY_REFERENCE_INDICES | GeometryArray.USE_COORD_INDEX_ONLY,
					terrainData.indexesCount, terrainData.stripCounts);
			physicsTriStripArray.setCoordRefBuffer(new J3DBuffer(Utils3D.makeFloatBuffer(terrainData.coordinates)));
			physicsTriStripArray.setCoordIndicesRef(terrainData.indexes);

			//apply them
			physicsTriStripArray.setName("LAND phys geo");
			shape.setGeometry(physicsTriStripArray);
			shape.setAppearance(PhysAppearance.makeAppearance());
			addNodeChild(shape);

			//	gi = new GeometryInfo(GeometryInfo.TRIANGLE_STRIP_ARRAY);
			//	gi.setStripCounts(terrainData.stripCounts);
			//	gi.setCoordinates(terrainData.coordinates);
			//	gi.setCoordinateIndices(terrainData.indexes);
		}
	}

	public float[][] getHeights()
	{
		if (land.VHGT != null)
		{
			// extract the heights
			byte[] heightBytes = land.VHGT;
			return extractHeights(heightBytes);
		}
		return null;
	}

	/*	public GeometryInfo getGeometryInfo()
		{
			//Weird new build physics off visuals system
			if (gi == null)
			{
				if (land.VHGT != null)
				{
					// extract the heights
					byte[] heightBytes = land.VHGT;
					float[][] heights = extractHeights(heightBytes);
	
					//now translate the heights into a nice mesh, 82 has been confirmed empirically			
					//Note that 33 by 33 sets of point equals 32 by 32 sets of triangles between them
					TESLANDGen gridGenerator = new TESLANDGen(J3dLAND.LAND_SIZE, J3dLAND.LAND_SIZE, (GRID_COUNT + 1), (GRID_COUNT + 1), heights,
							null, null, null);
					GeometryData terrainData = new GeometryData();
					gridGenerator.generateIndexedTriangleStrips(terrainData);
	
					gi = new GeometryInfo(GeometryInfo.TRIANGLE_STRIP_ARRAY);
					gi.setStripCounts(terrainData.stripCounts);
					gi.setCoordinates(terrainData.coordinates);
					gi.setCoordinateIndices(terrainData.indexes);
				}
			}
			return gi;
		}*/

	/**
	 * makes the visual version of land
	 * @param land
	 * @param master
	 */

	private float lowestHeight = Float.MAX_VALUE;
	private float highestHeight = Float.MIN_VALUE;

	private static ShaderProgram shaderProgram = null;

	private static final Object shaderLock = new Object();

	public J3dLAND(LAND land, IRecordStore master, TextureSource textureSource)
	{
		super(land, false, false);
		if (land.tes3)
			tes3LAND(land, master, textureSource);
		else
			LAND(land, master, textureSource);
	}

	private void LAND(LAND land, IRecordStore master, TextureSource textureSource)
	{
		this.land = land;
		int quadrantsPerSide = 2;
		int totalQuadrants = quadrantsPerSide * quadrantsPerSide;
		int quadrantSquareCount = (GRID_COUNT / quadrantsPerSide) + 1;

		Group baseGroup = new Group();
		addNodeChild(baseGroup);

		//ensure shader ready
		createShaderProgram();

		if (land.VHGT != null)
		{
			// extract the heights
			byte[] heightBytes = land.VHGT;
			float[][] heights = extractHeights(heightBytes);

			// extract the normals
			byte[] normalBytes = land.VNML;
			Vector3f[][] normals = extractNormals(normalBytes);

			// extract the colors
			byte[] colorBytes = land.VCLR;
			Color4f[][] colors = extractColors(colorBytes);

			// get the atxts
			ATXT[] atxts = land.ATXTs;

			for (int quadrant = 0; quadrant < totalQuadrants; quadrant++)
			{
				ShaderAppearance app = new ShaderAppearance();
				app.setMaterial(createMat());
				//app.setRenderingAttributes(createRA());

				ArrayList<ShaderAttributeValue> allShaderAttributeValues = new ArrayList<ShaderAttributeValue>();
				ArrayList<TextureUnitState> allTextureUnitStates = new ArrayList<TextureUnitState>();

				TextureUnitState tus = null;

				//oddly btxt are optional
				BTXT btxt = land.BTXTs[quadrant];

				if (btxt != null)
				{
					tus = getTexture(btxt.textureFormID, master, textureSource);
				}
				else
				{
					tus = getDefaultTexture(textureSource);
				}

				if (tus == null)
					System.err.println("tus == null, things are gonna break!");

				allTextureUnitStates.add(tus);
				allShaderAttributeValues.add(new ShaderAttributeValue("baseMap", new Integer(0)));

				Shape3D baseQuadShape = new Shape3D();
				baseQuadShape.setAppearance(app);

				int[] attributeSizes = new int[] { 4, 4, 4 };
				GeometryArray ga = makeQuadrantBaseSubGeom(heights, normals, colors, quadrantsPerSide, quadrant, 1, 3, attributeSizes);
				ga.setName(land.toString() + ":LAND " + quadrant + " " + land.landX + " " + land.landY);

				baseQuadShape.setGeometry(ga);

				ByteBuffer bb = ByteBuffer.allocateDirect((quadrantSquareCount * quadrantSquareCount) * 4 * 4);
				bb.order(ByteOrder.nativeOrder());
				FloatBuffer alphas04 = bb.asFloatBuffer();
				bb = ByteBuffer.allocateDirect((quadrantSquareCount * quadrantSquareCount) * 4 * 4);
				bb.order(ByteOrder.nativeOrder());
				FloatBuffer alphas58 = bb.asFloatBuffer();
				bb = ByteBuffer.allocateDirect((quadrantSquareCount * quadrantSquareCount) * 4 * 4);
				bb.order(ByteOrder.nativeOrder());
				FloatBuffer alphas912 = bb.asFloatBuffer();

				//These are per sorted by layer in LAND RECO
				for (int a = 0; a < atxts.length; a++)
				{
					ATXT atxt = atxts[a];

					if (atxt.quadrant == quadrant)
					{
						// now build up the vertex attribute float arrays to hand to the geometry	
						VTXT vtxt = atxt.vtxt;
						if (vtxt != null)
						{

							tus = getTexture(atxt.textureFormID, master, textureSource);

							if (tus == null)
								System.err.println("tus == null, things are gonna break!");

							allTextureUnitStates.add(tus);
							//Notice +2 as space for base and size is one more than final index, these are in order so there should be no spaces
							if (allTextureUnitStates.size() != atxt.layer + 2)
								System.err.println("allTextureUnitStates.size()!= atxt.layer + 2 " + allTextureUnitStates.size() + " != "
										+ (atxt.layer + 2));

							allShaderAttributeValues.add(new ShaderAttributeValue("layerMap" + atxt.layer, new Integer(atxt.layer + 1)));

							for (int v = 0; v < vtxt.count; v++)
							{
								int rowno = (GRID_COUNT / quadrantsPerSide) - (vtxt.position[v] / quadrantSquareCount);
								int colno = (vtxt.position[v] % quadrantSquareCount);

								int idx = ((rowno * quadrantSquareCount + colno) * 4) + (atxt.layer % 4);
								if (atxt.layer < 4)
									alphas04.put(idx, vtxt.opacity[v]);
								else if (atxt.layer < 8)
									alphas58.put(idx, vtxt.opacity[v]);
								else if (atxt.layer < 12)
									alphas912.put(idx, vtxt.opacity[v]);
								else
									System.out.println("atxt.layer== " + atxt.layer);
							}
						}
					}
				}

				ga.setVertexAttrRefBuffer(0, new J3DBuffer(alphas04));
				ga.setVertexAttrRefBuffer(1, new J3DBuffer(alphas58));
				ga.setVertexAttrRefBuffer(2, new J3DBuffer(alphas912));

				TextureUnitState[] tusa = new TextureUnitState[allTextureUnitStates.size()];
				for (int i = 0; i < allTextureUnitStates.size(); i++)
				{
					tusa[i] = allTextureUnitStates.get(i);
					//TODO: I notice the same texture repeats in the layers a lot sometimes
					if (OUTPUT_BINDINGS)
						System.out.println("LAND Tus " + i + " " + tusa[i]);
				}
				app.setTextureUnitState(tusa);

				app.setShaderProgram(shaderProgram);

				ShaderAttributeSet shaderAttributeSet = new ShaderAttributeSet();
				for (ShaderAttributeValue sav : allShaderAttributeValues)
				{
					if (OUTPUT_BINDINGS)
						System.out.println(sav.getAttributeName() + " " + sav.getValue());

					shaderAttributeSet.put(sav);
				}
				app.setShaderAttributeSet(shaderAttributeSet);

				baseGroup.addChild(baseQuadShape);

			}

		}
	}

	private static Material mat;

	public static Material createMat()
	{
		if (mat == null)
		{
			mat = new Material();
			mat.setColorTarget(Material.AMBIENT_AND_DIFFUSE);
			mat.setShininess(1.0f);
			mat.setDiffuseColor(1f, 1f, 1f);
			mat.setSpecularColor(1f, 1f, 1f);
		}
		return mat;
	}

	private static RenderingAttributes ra;

	public static RenderingAttributes createRA()
	{
		if (ra == null)
		{
			ra = new RenderingAttributes();
		}
		return ra;
	}

	protected static Vector3f quadOffSet(int quadrantsPerSide, int quadrant)
	{
		//Yes it's mad, but get a pen and paper and this is where a quadrant is

		float quadSize = LAND_SIZE / quadrantsPerSide;
		float halfQuadSize = quadSize / 2f;

		int qx = quadrant % quadrantsPerSide;
		int qy = quadrant / quadrantsPerSide;

		//-1 handles odd sizes
		float x = ((qx - (quadrantsPerSide / 2f)) * quadSize) + halfQuadSize;
		float y = ((qy - (quadrantsPerSide / 2f)) * quadSize) + halfQuadSize;
		return new Vector3f(x, 0, -y);
	}

	protected static GeometryArray makeQuadrantBaseSubGeom(float[][] heights, Vector3f[][] normals, Color4f[][] colors,
			int quadrantsPerSide, int quadrant, int texCoordCount, int vertexAttrCount, int[] vertexAttrSizes)
	{
		int quadrantSquareCount = (GRID_COUNT / quadrantsPerSide) + 1;
		float[][] quadrantHeights = new float[quadrantSquareCount][quadrantSquareCount];
		Vector3f[][] quadrantNormals = new Vector3f[quadrantSquareCount][quadrantSquareCount];
		Color4f[][] quadrantColors = new Color4f[quadrantSquareCount][quadrantSquareCount];
		TexCoord2f[][] quadrantTexCoords = new TexCoord2f[quadrantSquareCount][quadrantSquareCount];

		makeQuadrantData(quadrantsPerSide, quadrant, heights, normals, colors, quadrantHeights, quadrantNormals, quadrantColors,
				quadrantTexCoords);

		//Note that 33 by 33 sets of point equals 32 by 32 set of triangles between them
		TESLANDGen gridGenerator = new TESLANDGen(LAND_SIZE / quadrantsPerSide, LAND_SIZE / quadrantsPerSide, quadrantSquareCount,
				quadrantSquareCount, quadrantHeights, quadrantNormals, quadrantColors, quadrantTexCoords);

		GeometryData terrainData = new GeometryData();

		//generator generates madness
		//if (STRIPIFY)
		//	gridGenerator.generateIndexedTriangleStrips(terrainData);
		//else
		gridGenerator.generateIndexedTriangles(terrainData);

		//offset for quadrant and location
		Vector3f offset = quadOffSet(quadrantsPerSide, quadrant);
		for (int i = 0; i < terrainData.coordinates.length; i += 3)
		{
			terrainData.coordinates[i + 0] += offset.x;
			terrainData.coordinates[i + 1] += offset.y;
			terrainData.coordinates[i + 2] += offset.z;
		}

		return createGA(terrainData, texCoordCount, vertexAttrCount, vertexAttrSizes);

	}

	/**
	 * 
	 * @param quadrant Specifies the quadrant this BTXT record applies to. 0 = bottom left. 1 = bottom right. 2 = upper-left. 3 = upper-right.
	 * @param quadrant2 
	 * @param baseHeights 33x33 array of all 4 quads
	 * @param baseNormals 33x33 array of all 4 quads
	 * @param baseColors  33x33 array of all 4 quads
	 * @param quadrantHeights 17x17 array to be filled
	 * @param quadrantNormals 17x17 array to be filled
	 * @param quadrantColors  17x17 array to be filled
	 */
	private static void makeQuadrantData(int quadrantsPerSide, int quadrant, float[][] baseHeights, Vector3f[][] baseNormals,
			Color4f[][] baseColors, float[][] quadrantHeights, Vector3f[][] quadrantNormals, Color4f[][] quadrantColors,
			TexCoord2f[][] quadrantTexCoords)
	{
		//trust me on this madness
		int qx = quadrant % quadrantsPerSide;
		int qy = quadrant / quadrantsPerSide;

		int quadrant_grid_count = (GRID_COUNT / quadrantsPerSide);

		for (int row = 0; row < quadrant_grid_count + 1; row++)
		{
			for (int col = 0; col < quadrant_grid_count + 1; col++)
			{
				int baseRow = row + (((quadrantsPerSide - 1) - qy) * quadrant_grid_count);
				int baseCol = col + ((qx) * quadrant_grid_count);
				quadrantHeights[row][col] = baseHeights[baseRow][baseCol];
				quadrantNormals[row][col] = baseNormals[baseRow][baseCol];
				quadrantColors[row][col] = baseColors[baseRow][baseCol];
				quadrantTexCoords[row][col] = new TexCoord2f((row * TEX_REPEAT), (col * TEX_REPEAT));
			}
		}
	}

	public static TextureUnitState getTextureTes3(int textureID, IRecordStore master, TextureSource textureSource)
	{
		return getTextureTes3(textureID, master, textureSource, false);
	}
	public static TextureUnitState getTextureTes3(int textureID, IRecordStore master, TextureSource textureSource, boolean dropMip0)
	{
		//0 means default?
		if (textureID > 0)
		{
			//not sure why -1 has correct texture but it sure does see openMW
			Record ltexRec = ((IRecordStoreTes3) master).getRecord("LTEX_" + (textureID - 1));
			if (ltexRec != null)
			{
				if (ltexRec.getRecordType().equals("LTEX"))
				{
					LTEX ltex = new LTEX(ltexRec);
					if (ltex.ICON != null)
					{
						TextureUnitState tus = J3dNiGeometry.loadTextureUnitState(ltex.ICON, textureSource, dropMip0);
						if (tus != null)
						{
							return tus;
						}
					}
				}
				else
				{
					System.out.println("Tes3 Bad textureFormID " + textureID + " type is not LTEX but " + ltexRec.getRecordType());
				}
			}
		}
		return getDefaultTexture(textureSource);
	}

	public static TextureUnitState getTexture(int textureFormID, IRecordStore master, TextureSource textureSource)
	{
		if (textureFormID > 0)
		{
			Record ltexRec = master.getRecord(textureFormID);
			if (ltexRec.getRecordType().equals("LTEX"))
			{
				TextureUnitState tus = null;
				LTEX ltex = new LTEX(ltexRec);
				int texSetId = ltex.textureSetId;

				if (texSetId != -1)
				{
					Record texSetRec = master.getRecord(texSetId);
					TXST textureSet = new TXST(texSetRec);
					if (textureSet.TX00 != null)
					{
						tus = textureSource.getTextureUnitState(textureSet.TX00);
					}
					else if (textureSet.MNAM != null)
					{
						// new fallout 4 texture system
						try
						{
							BSMaterial material = BgsmSource.bgsmSource.getMaterial("Materials\\" + textureSet.MNAM);
							if (material != null)
							{
								tus = textureSource.getTextureUnitState(((ShaderMaterial)material).DiffuseTexture);
							}

						}
						catch (IOException e)
						{
							e.printStackTrace();
						}
					}
				}
				else if (ltex.ICON != null)
				{
					//obliv uses simpler system					
					tus = textureSource.getTextureUnitState("Landscape\\" + ltex.ICON);
				}
				return tus;
			}
			else
			{
				System.out.println("Bad textureFormID " + textureFormID + " type is not LTEX but " + ltexRec.getRecordType());
			}

		}
		return getDefaultTexture(textureSource);

	}

	private static TextureUnitState defaultTex = null;

	protected static TextureUnitState getDefaultTexture(TextureSource textureSource)
	{
		//Skyrim //textures\\landscape\\dirt01.dds
		//FO3 //textures\\landscape\\dirt01.dds
		//Obliv //textures\\landscape\\default.dds
		if (defaultTex == null)
		{
			if (textureSource.textureFileExists("Landscape\\dirt01.dds"))
			{
				defaultTex = textureSource.getTextureUnitState("Landscape\\dirt01.dds");
			}
			else if (textureSource.textureFileExists("Landscape\\default.dds"))
			{
				defaultTex = textureSource.getTextureUnitState("Landscape\\default.dds");
			}
			else if (textureSource.textureFileExists("_land_default.dds"))
			{
				defaultTex = textureSource.getTextureUnitState("_land_default.dds");
			}
			else if (textureSource.textureFileExists("Landscape\\Ground\\BlastedForestDirt01_d.DDS"))
			{
				defaultTex = textureSource.getTextureUnitState("Landscape\\Ground\\BlastedForestDirt01_d.DDS");
			}
			else
			{
				System.out.println("BUM, no default LAND texture found somehow?");
			}
		}
		return defaultTex;
	}

	private float[][] extractHeights(byte[] heightBytes)
	{
		// extract the heights
		float[][] heights = new float[(GRID_COUNT + 1)][(GRID_COUNT + 1)];

		float startHeightOffset = ESMByteConvert.extractFloat(heightBytes, 0);

		float startRowHeight = (startHeightOffset * 4);
		for (int row = 0; row < (GRID_COUNT + 1); row++)
		{
			float height = startRowHeight;
			for (int col = 0; col < (GRID_COUNT + 1); col++)
			{
				int idx = col + (row * (GRID_COUNT + 1)) + 4;//+4 is start float
				
				// not sure why this exception in oblivion
				// java.lang.ArrayIndexOutOfBoundsException: length=1096; index=1096
            	//at esmj3d.j3d.j3drecords.inst.J3dLAND.extractHeights(J3dLAND.java:652)
				if(idx < heightBytes.length)
					height += heightBytes[idx] * 4;

				// start next row relative to the start of this row
				if (col == 0)
					startRowHeight = height;

				// note reverse order, due to x,y,z => x,z,-y
				float h = (height * HEIGHT_TO_J3D_SCALE);
				heights[GRID_COUNT - row][col] = h;

				//update lowest
				lowestHeight = h < lowestHeight ? h : lowestHeight;
				highestHeight = h > highestHeight ? h : highestHeight;
			}
		}

		//last 3 bytes, what are they?
		// Unknown. Haven't noticed any ill-effects just filling this with arbitrary values in TES3 or TES4. 
		// This is probably just a 3-byte filler so that the entire subrecord's data can be aligned on a 4 byte word boundary.

		return heights;

	}

	protected static Vector3f[][] extractNormals(byte[] normalBytes)
	{
		Vector3f[][] normals = new Vector3f[(GRID_COUNT + 1)][(GRID_COUNT + 1)];
		for (int row = 0; row < (GRID_COUNT + 1); row++)
		{
			for (int col = 0; col < (GRID_COUNT + 1); col++)
			{
				byte x = normalBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 0];
				byte y = normalBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 1];
				byte z = normalBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 2];

				Vector3f v = new Vector3f(x & 0xff, z & 0xff, -y & 0xff);
				v.normalize();
				// note reverse order, due to x,y,z => x,z,-y
				normals[GRID_COUNT - row][col] = v;
			}
		}
		return normals;
	}

	protected static Color4f[][] extractColors(byte[] colorBytes)
	{

		Color4f[][] colors = new Color4f[(GRID_COUNT + 1)][(GRID_COUNT + 1)];

		for (int row = 0; row < (GRID_COUNT + 1); row++)
		{
			for (int col = 0; col < (GRID_COUNT + 1); col++)
			{
				if (colorBytes != null)
				{
					float r = (colorBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 0] & 0xff) / 255f;
					float g = (colorBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 1] & 0xff) / 255f;
					float b = (colorBytes[(col + (row * (GRID_COUNT + 1))) * 3 + 2] & 0xff) / 255f;
					Color4f c = new Color4f(r, g, b, 1.0f);//note hard coded opaque

					// note reverse order, due to x,y,z => x,z,-y
					colors[GRID_COUNT - row][col] = c;
				}
				else
				{
					// no colors let's try white
					colors[GRID_COUNT - row][col] = new Color4f(1.0f, 1.0f, 1.0f, 1.0f);
				}

			}
		}

		return colors;
	}

	/**
	 * texCoordCount is overrriden to 1
	 * @param terrainData
	 * @param texCoordCount
	 * @param vertexAttrCount
	 * @param vertexAttrSizes
	 * @return
	 */
	public static GeometryArray createGA(GeometryData terrainData, int texCoordCount, int vertexAttrCount, int[] vertexAttrSizes)
	{
		int basicFormat = GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.COLOR_4 //
				| GeometryArray.TEXTURE_COORDINATE_2 //
				| GeometryArray.USE_COORD_INDEX_ONLY //
				| (BY_REF || STRIPIFY ? (GeometryArray.BY_REFERENCE_INDICES | GeometryArray.BY_REFERENCE) : 0)//
				| (BUFFERS ? GeometryArray.USE_NIO_BUFFER : 0) //
				| (vertexAttrCount > 0 ? GeometryArray.VERTEX_ATTRIBUTES : 0);

		texCoordCount = 1;
		int[] texMap = new int[texCoordCount];
		for (int i = 0; i < texCoordCount; i++)
			texMap[i] = i;

		IndexedGeometryArray iga;
		if (INTERLEAVE)
		{
			if (STRIPIFY)
			{
				iga = new IndexedTriangleStripArray(terrainData.vertexCount, basicFormat | GeometryArray.INTERLEAVED, //
						texCoordCount, texMap, vertexAttrCount, vertexAttrSizes, terrainData.indexesCount, terrainData.stripCounts);
			}
			else
			{
				iga = new IndexedTriangleArray(terrainData.vertexCount, basicFormat | GeometryArray.INTERLEAVED, //
						texCoordCount, texMap, vertexAttrCount, vertexAttrSizes, terrainData.indexesCount);
			}
			iga.setCoordIndicesRef(terrainData.indexes);

			float[] vertexData = J3dNiTriBasedGeom.interleave(2, new float[][] { terrainData.textureCoordinates }, null, terrainData.colors,
					terrainData.normals, terrainData.coordinates);

			if (!BUFFERS)
			{
				iga.setInterleavedVertices(vertexData);
			}
			else
			{
				iga.setInterleavedVertexBuffer(new J3DBuffer(Utils3D.makeFloatBuffer(vertexData)));
			}

		}
		else
		{
			if (STRIPIFY)
			{
				//	iga=null;
				iga = new IndexedTriangleStripArray(terrainData.vertexCount, basicFormat, texCoordCount, texMap, vertexAttrCount,
						vertexAttrSizes, //
						terrainData.indexesCount, terrainData.stripCounts);
			}
			else
			{
				iga = new IndexedTriangleArray(terrainData.vertexCount, basicFormat, texCoordCount, texMap, vertexAttrCount,
						vertexAttrSizes, //
						terrainData.indexesCount);
			}

			if (!BY_REF)
			{
				iga.setCoordinates(0, terrainData.coordinates);
				iga.setCoordinateIndices(0, terrainData.indexes);
				iga.setNormals(0, terrainData.normals);
				iga.setColors(0, terrainData.colors);
				iga.setTextureCoordinates(0, 0, terrainData.textureCoordinates);
			}
			else
			{
				if (!BUFFERS)
				{
					iga.setCoordRefFloat(terrainData.coordinates);
					iga.setCoordIndicesRef(terrainData.indexes);
					iga.setNormalRefFloat(terrainData.normals);
					iga.setColorRefFloat(terrainData.colors);
					iga.setTexCoordRefFloat(0, terrainData.textureCoordinates);
				}
				else
				{

					/*			GeometryInfo gi = new GeometryInfo(GeometryInfo.TRIANGLE_ARRAY);
								gi.setCoordinateIndices(terrainData.indexes);
								gi.setUseCoordIndexOnly(true);
								gi.setCoordinates(terrainData.coordinates);
								gi.setColors4(terrainData.colors);
								gi.setNormals(terrainData.normals);
								gi.setTextureCoordinateParams(1, 2);
								gi.setTextureCoordinates(0, terrainData.textureCoordinates);
					
							
								Stripifier stripifer = new Stripifier();
								stripifer.stripify(gi);
								iga = gi.getIndexedGeometryArray(true, true, false, true, true);
					*/

					iga.setCoordRefBuffer(new J3DBuffer(Utils3D.makeFloatBuffer(terrainData.coordinates)));
					iga.setCoordIndicesRef(terrainData.indexes);
					iga.setNormalRefBuffer(new J3DBuffer(Utils3D.makeFloatBuffer(terrainData.normals)));
					iga.setColorRefBuffer(new J3DBuffer(Utils3D.makeFloatBuffer(terrainData.colors)));
					iga.setTexCoordRefBuffer(0, new J3DBuffer(Utils3D.makeFloatBuffer(terrainData.textureCoordinates)));

				}
			}

		}

		return iga;

	}

	@Override
	public String toString()
	{
		return this.getClass().getSimpleName();
	}

	private static void createShaderProgram()
	{// in case 2 threads come in trying to lazy create
		synchronized (shaderLock)
		{
			if (shaderProgram == null)
			{
				String vertexProgram = ShaderSourceIO.getTextFileAsString("shaders/land.vert");
				String fragmentProgram = ShaderSourceIO.getTextFileAsString("shaders/land.frag");

				Shader[] shaders = new Shader[2];
				shaders[0] = new SourceCodeShader(Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX, vertexProgram) {
					@Override
					public String toString()
					{
						return "vertexProgram";
					}
				};
				shaders[1] = new SourceCodeShader(Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, fragmentProgram) {
					@Override
					public String toString()
					{
						return "fragmentProgram";
					}
				};

				shaderProgram = new GLSLShaderProgram() {
					@Override
					public String toString()
					{
						return "Land Shader Program";
					}
				};
				shaderProgram.setShaders(shaders);

				String[] shaderAttrNames = new String[10];

				shaderAttrNames[0] = "baseMap";
				for (int i = 0; i < 9; i++)
				{
					shaderAttrNames[i + 1] = "layerMap" + i;
					if (OUTPUT_BINDINGS)
						System.out.println("shaderAttrNames " + shaderAttrNames[i]);
				}

				shaderProgram.setShaderAttrNames(shaderAttrNames);

				String[] vertexAttrNames = new String[] { "alphas04", "alphas58", "alphas912" };
				shaderProgram.setVertexAttrNames(vertexAttrNames);

			}
		}
	}

	public float getLowestHeight()
	{
		return lowestHeight;
	}
	public float getHighestHeight()
	{
		return highestHeight;
	}
	
	public void tes3LAND(LAND land, IRecordStore master, TextureSource textureSource)
	{
		this.land = land;
		int quadrantsPerSide = 16;

		Group baseGroup = new Group();
		addNodeChild(baseGroup);

		//ensure shader ready
		createShaderProgramTes3();

		if (land.VHGT != null)
		{
			// extract the heights
			byte[] heightBytes = land.VHGT;
			float[][] heights = extractHeights(heightBytes);

			// extract the normals
			byte[] normalBytes = land.VNML;
			Vector3f[][] normals = extractNormals(normalBytes);

			// extract the colors
			byte[] colorBytes = land.VCLR;
			Color4f[][] colors = extractColors(colorBytes);

			ShaderAppearance app = new ShaderAppearance();
			app.setMaterial(createMat());
			//app.setRenderingAttributes(createRA());

			Shape3D baseQuadShape = new Shape3D();
			baseQuadShape.setAppearance(app);

			int[] attributeSizes = new int[] { 4, 4, 4, 4 };
			GeometryArray ga = makeQuadrantBaseSubGeom(heights, normals, colors, 1, 0, 1, 4, attributeSizes);
			ga.setName(land.toString() + ":LAND " + 0 + " " + land.landX + " " + land.landY);

			baseQuadShape.setGeometry(ga);

			ArrayList<ShaderAttributeValue> allShaderAttributeValues = new ArrayList<ShaderAttributeValue>();
			ArrayList<TextureUnitState> allTextureUnitStates = new ArrayList<TextureUnitState>();

			SparseArray<Integer> texIdToTUS = new SparseArray<Integer>();
			int tusCount = 0;
			//16x16 texids each one is for a set of 4x4 squares (5x5 verts make up the square) 
			//max seen 14
			for (int t = 0; t < land.VTEXshorts.length; t++)
			{
				int texFormId = land.VTEXshorts[t];
				//ensure the TUS exists and we have a map to it's sampler id
				if (texIdToTUS.get(texFormId) == null)
				{
					TextureUnitState tus = getTextureTes3(texFormId, master, textureSource);
					allTextureUnitStates.add(tus);
					allShaderAttributeValues.add(new ShaderAttributeValue("sampler" + tusCount, new Integer(tusCount)));
					texIdToTUS.put(texFormId, new Integer(tusCount));
					//System.out.println("t " + t + " putting texid = " + texFormId + " against " + tusCount);
					tusCount++;
				}
			}

			int vertsPerSide = (GRID_COUNT + 1);
			int vertexCount = vertsPerSide * vertsPerSide;
			ByteBuffer bb = ByteBuffer.allocateDirect(vertexCount * 4 * 4);
			bb.order(ByteOrder.nativeOrder());
			FloatBuffer samplers0 = bb.asFloatBuffer();
			bb = ByteBuffer.allocateDirect(vertexCount * 4 * 4);
			bb.order(ByteOrder.nativeOrder());
			FloatBuffer samplers1 = bb.asFloatBuffer();
			bb = ByteBuffer.allocateDirect(vertexCount * 4 * 4);
			bb.order(ByteOrder.nativeOrder());
			FloatBuffer samplers2 = bb.asFloatBuffer();
			bb = ByteBuffer.allocateDirect(vertexCount * 4 * 4);
			bb.order(ByteOrder.nativeOrder());
			FloatBuffer samplers3 = bb.asFloatBuffer();
			for (int row = 0; row < vertsPerSide; row++)
			{
				for (int col = 0; col < vertsPerSide; col++)
				{
					int vertexIdx = (row * vertsPerSide) + col;

					int quadRow = (row / 4);
					int quadCol = (col / 4);
					//put final verts into prev quadrant (each has 5 lines of verts reused by the next except for the final)
					quadRow = quadRow >= quadrantsPerSide ? quadrantsPerSide - 1 : quadRow;
					quadCol = quadCol >= quadrantsPerSide ? quadrantsPerSide - 1 : quadCol;

					int quadrant = ((((quadrantsPerSide - 1) - quadRow) * quadrantsPerSide)) + quadCol;
					if (quadrant < land.VTEXshorts.length)
					{
						// look up sampler id from texture id mapped earlier
						int samplerId = texIdToTUS.get(land.VTEXshorts[quadrant]);
						//	System.out.println("quadRow " + quadRow + "  " + quadCol + " quadrant = " + quadrant + " vertexIdx " + vertexIdx
						//			+ " land.VTEXshorts[quadrant] " + land.VTEXshorts[quadrant] + " sampler id = " + samplerId);

						//NOTICE 1s! as this is the base only
						// 0.5 etc are for the layers in a moment

						int idx = (vertexIdx * 4) + (samplerId % 4);
						if (samplerId < 4)
							samplers0.put(idx, 1);
						else if (samplerId < 8)
							samplers1.put(idx, 1);
						else if (samplerId < 12)
							samplers2.put(idx, 1);
						else if (samplerId < 16)
							samplers3.put(idx, 1);
						else
							new Throwable("SamplerId too big! " + samplerId).printStackTrace();
					}
				}
			}

			ga.setVertexAttrRefBuffer(0, new J3DBuffer(samplers0));
			ga.setVertexAttrRefBuffer(1, new J3DBuffer(samplers1));
			ga.setVertexAttrRefBuffer(2, new J3DBuffer(samplers2));
			ga.setVertexAttrRefBuffer(3, new J3DBuffer(samplers3));

			TextureUnitState[] tusa = new TextureUnitState[allTextureUnitStates.size()];
			for (int i = 0; i < allTextureUnitStates.size(); i++)
			{
				tusa[i] = allTextureUnitStates.get(i);
				//TODO: I notice the same texture repeats in the layers a lot sometimes
				if (OUTPUT_BINDINGS)
					System.out.println("LAND Tus " + i + " " + tusa[i]);
			}
			app.setTextureUnitState(tusa);

			app.setShaderProgram(shaderProgram);

			ShaderAttributeSet shaderAttributeSet = new ShaderAttributeSet();
			for (ShaderAttributeValue sav : allShaderAttributeValues)
			{
				if (OUTPUT_BINDINGS)
					System.out.println(sav.getAttributeName() + " " + sav.getValue());

				shaderAttributeSet.put(sav);
			}
			app.setShaderAttributeSet(shaderAttributeSet);

			baseGroup.addChild(baseQuadShape);

		}

	}

	private static void createShaderProgramTes3()
	{
		// in case 2 threads come in trying to lazy create
		synchronized (shaderLock)
		{
			if (shaderProgram == null)
			{
				String vertexProgram = ShaderSourceIO.getTextFileAsString("shaders/landtes3.vert");
				String fragmentProgram = ShaderSourceIO.getTextFileAsString("shaders/landtes3.frag");

				Shader[] shaders = new Shader[2];
				shaders[0] = new SourceCodeShader(Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX, vertexProgram) {
					@Override
					public String toString()
					{
						return "Tes3 land vertex Program";
					}
				};
				shaders[1] = new SourceCodeShader(Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, fragmentProgram) {
					@Override
					public String toString()
					{
						return "Tes3 land fragment Program";
					}
				};

				shaderProgram = new GLSLShaderProgram() {
					@Override
					public String toString()
					{
						return "Land Shader Program";
					}
				};
				shaderProgram.setShaders(shaders);

				String[] shaderAttrNames = new String[20];
				for (int i = 0; i < 20; i++)
				{
					shaderAttrNames[i] = "sampler" + i;
					if (OUTPUT_BINDINGS)
						System.out.println("shaderAttrNames " + shaderAttrNames[i]);
				}

				shaderProgram.setShaderAttrNames(shaderAttrNames);

				String[] vertexAttrNames = new String[] { "samplers0", "samplers1", "samplers2", "samplers3" };
				shaderProgram.setVertexAttrNames(vertexAttrNames);

			}
		}
	}

}
