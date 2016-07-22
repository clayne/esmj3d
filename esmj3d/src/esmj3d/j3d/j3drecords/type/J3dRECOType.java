package esmj3d.j3d.j3drecords.type;

import java.util.ArrayList;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Node;
import javax.vecmath.Color3f;

import esmj3d.data.shared.records.RECO;
import esmj3d.j3d.BethRenderSettings;
import nif.NifJ3dHavokRoot;
import nif.NifJ3dVisRoot;
import nif.NifToJ3d;
import nif.character.NifJ3dSkeletonRoot;
import nif.j3d.J3dNiAVObject;
import nif.j3d.J3dNiSkinInstance;
import nif.j3d.animation.J3dNiControllerSequence;
import nif.j3d.particles.J3dNiParticleSystem;
import tools3d.utils.Utils3D;
import tools3d.utils.scenegraph.Fadable;
import tools3d.utils.scenegraph.VaryingLODBehaviour;
import utils.source.MediaSources;

public abstract class J3dRECOType extends BranchGroup implements Fadable
{
	private RECO RECO = null;

	public String physNifFile = "";

	public String shortName = "";//very temp helper

	protected J3dNiAVObject j3dNiAVObject;

	private ArrayList<J3dNiSkinInstance> allSkins;
	private NifJ3dSkeletonRoot inputSkeleton;

	public J3dRECOType(RECO RECO, String physNifFile)
	{
		clearCapabilities();
		this.RECO = RECO;
		this.physNifFile = physNifFile;
		if (physNifFile != null && physNifFile.lastIndexOf("\\") != -1)
			shortName = physNifFile.substring(physNifFile.lastIndexOf("\\") + 1, physNifFile.length() - 4);
	}

	public RECO getRECO()
	{
		return RECO;
	}

	public int getRecordId()
	{
		return RECO.getRecordId();
	}

	public void renderSettingsUpdated()
	{
		J3dNiParticleSystem.setSHOW_DEBUG_LINES(BethRenderSettings.isOutlineParts());
	}

	@Override
	public void fade(float percent)
	{
		if (j3dNiAVObject != null && j3dNiAVObject instanceof Fadable)
		{
			((Fadable) j3dNiAVObject).fade(percent);
		}
	}

	public abstract void setOutlined(boolean b);

	@Override
	public void setOutline(Color3f c)
	{
		if (j3dNiAVObject != null && j3dNiAVObject instanceof Fadable)
		{
			((Fadable) j3dNiAVObject).setOutline(c);
		}
	}

	/**
	 *  animate skins, notice only for visuals at this stage
	 */
	protected void fireIdle(NifJ3dVisRoot nvr)
	{
		fireIdle();

		//Fire off any skins
		if (j3dNiAVObject != null)
		{
			if (NifJ3dSkeletonRoot.isSkeleton(nvr.getNiToJ3dData()))
			{

				inputSkeleton = new NifJ3dSkeletonRoot(nvr);
				// create skins from the skeleton and skin nif
				allSkins = J3dNiSkinInstance.createSkins(nvr.getNiToJ3dData(), inputSkeleton);

				if (allSkins.size() > 0)
				{
					// add the skins to the scene
					for (J3dNiSkinInstance j3dNiSkinInstance : allSkins)
					{
						addChild(j3dNiSkinInstance);
					}

					SkinUpdateBehavior pub = new SkinUpdateBehavior(j3dNiAVObject, new float[] { 10, 20, 100 });
					addChild(pub);
					addChild(inputSkeleton);
				}
			}
		}

	}

	protected void fireIdle()
	{
		//TODO: some texture transforms appear to be a bit shakey?
		//fire the first idle
		if (j3dNiAVObject.getJ3dNiControllerManager() != null)
		{
			String[] seqNames = j3dNiAVObject.getJ3dNiControllerManager().getAllSequences();
			for (String seqName : seqNames)
			{
				if (seqName.toLowerCase().indexOf("idle") != -1)
				{
					long randStart = (long) (Math.random() * 500);
					J3dNiControllerSequence seq = j3dNiAVObject.getJ3dNiControllerManager().getSequence(seqName);
					if (seq.isNotRunning())
						seq.fireSequence(randStart);
					else
						System.out.println(
								"refiring " + seqName + " for " + j3dNiAVObject + " : " + j3dNiAVObject.getNiAVObject().nVer.fileName);
					break;
				}
			}
		}

	}

	public static J3dNiAVObject loadNif(String nifFileName, boolean makePhys, MediaSources mediaSources)
	{
		J3dNiAVObject j3dNiAVObject;

		if (makePhys)
		{
			NifJ3dHavokRoot nhr = NifToJ3d.loadHavok(nifFileName, mediaSources.getMeshSource());
			if (nhr == null)
				return null;
			j3dNiAVObject = nhr.getHavokRoot();
		}
		else
		{
			NifJ3dVisRoot nvr = NifToJ3d.loadShapes(nifFileName, mediaSources.getMeshSource(), mediaSources.getTextureSource());
			if (nvr == null)
				return null;
			j3dNiAVObject = nvr.getVisualRoot();
		}

		return j3dNiAVObject;

	}

	class SkinUpdateBehavior extends VaryingLODBehaviour
	{
		public SkinUpdateBehavior(Node node, float[] dists)
		{
			super(node, dists, true, true);
			setSchedulingBounds(Utils3D.defaultBounds);
		}

		@Override
		public void initialize()
		{
			super.initialize();
		}

		@Override
		public void process()
		{
			// must be called to update the accum transform
			inputSkeleton.updateBones();
			for (J3dNiSkinInstance j3dNiSkinInstance : allSkins)
			{
				j3dNiSkinInstance.processSkinInstance();
			}
		}

	}

	@Override
	public String toString()
	{
		return "" + this.getClass().getSimpleName() + " id " + RECO.getRecordId() + " : " + physNifFile;
	}

}
