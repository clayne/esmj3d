package esmj3d.j3d.cell;

import java.awt.Rectangle;

import javax.media.j3d.LinearFog;
import javax.vecmath.Color3f;

import tools3d.utils.Utils3D;

public class BethNoLodManager extends BethLodManager
{
	//TODO: can't I do something!!!!
	public BethNoLodManager(J3dICellFactory j3dCellFactory)
	{
		//create a hillarious distance fog
		//LinearFog, ExponentialFog
		LinearFog fog = new LinearFog(new Color3f(0.8f, 0.8f, 0.8f), 500, 3500);
		fog.addScope(this);
		fog.setInfluencingBounds(Utils3D.defaultBounds);
		addChild(fog);
	}

	public void setWorldFormId(int worldFormId)
	{

	}

	public void updateGross(float charX, float charY)
	{
	}

	public Rectangle getGridBounds(float charX, float charY, int nearLoadGridCount)
	{
		return Beth32LodManager.getBounds(charX, charY, nearLoadGridCount);
	}
}