package esmj3d.j3d.j3drecords.type;

import org.jogamp.java3d.utils.shader.Cube;
import org.jogamp.vecmath.Color3f;

import esmj3d.data.shared.records.GenericDOOR;
import esmj3d.j3d.BethRenderSettings;
import esmj3d.j3d.j3drecords.Doorable;
import nif.NifToJ3d;
import tools3d.utils.scenegraph.Fadable;
import utils.source.MediaSources;

public class J3dDOOR extends J3dRECOType implements Doorable
{
	private boolean isOpen = false;

	private boolean outlineSetOn = false;

	private Color3f outlineColor = new Color3f(1.0f, 0.5f, 0f);

	private GenericDOOR reco;

	public J3dDOOR(GenericDOOR reco, boolean makePhys, MediaSources mediaSources)
	{
		super(reco, reco.MODL.model, mediaSources);
		this.reco = reco;

		if (makePhys)
		{
			j3dNiAVObject = NifToJ3d.loadHavok(reco.MODL.model, mediaSources.getMeshSource()).getHavokRoot();
		}
		else
		{
			j3dNiAVObject = NifToJ3d.loadShapes(reco.MODL.model, mediaSources.getMeshSource(), mediaSources.getTextureSource())
					.getVisualRoot();
		}

		if (j3dNiAVObject != null)
		{
			//prep for possible outlines later
			if (j3dNiAVObject instanceof Fadable && !makePhys)
			{
				((Fadable) j3dNiAVObject).setOutline(outlineColor);
				if (!BethRenderSettings.isOutlineDoors())
					((Fadable) j3dNiAVObject).setOutline(null);
			}

			addChild(j3dNiAVObject);
			fireIdle();
		}
		
	 

	}

	@Override
	public void renderSettingsUpdated()
	{
		super.renderSettingsUpdated();
		if (j3dNiAVObject != null)
		{
			if (j3dNiAVObject instanceof Fadable)
			{
				Color3f c = BethRenderSettings.isOutlineDoors() || outlineSetOn ? outlineColor : null;
				((Fadable) j3dNiAVObject).setOutline(c);
			}
		}
	}

	@Override
	public void setOutlined(boolean b)
	{
		outlineSetOn = b;
		if (j3dNiAVObject != null)
		{
			if (j3dNiAVObject instanceof Fadable)
			{
				Color3f c = BethRenderSettings.isOutlineDoors() || outlineSetOn ? outlineColor : null;
				((Fadable) j3dNiAVObject).setOutline(c);
			}
		}
	}

	@Override
	public void toggleOpen()
	{
		isOpen = !isOpen;
		animateDoor();
	}

	public void setOpen(boolean isOpen)
	{
		this.isOpen = isOpen;
		animateDoor();
	}

	@Override
	public boolean isOpen()
	{
		return isOpen;
	}

	/**
	 * called after open is set
	 */
	protected void animateDoor()
	{
		if (j3dNiAVObject.getJ3dNiControllerManager() != null)
		{
			j3dNiAVObject.getJ3dNiControllerManager().getSequence(isOpen ? "Open" : "Close").fireSequenceOnce();
		}

	}

	@Override
	public String getDoorName()
	{
		if (reco.FULL != null)
			return reco.FULL.str;
		return "";

	}

	@Override
	public void playBothSounds()
	{
		// TODO Auto-generated method stub
		
	}

}
