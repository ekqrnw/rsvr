/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vr;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.RenderCallbackManager;
import org.joml.Vector3f;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

@Slf4j
class SceneUploader2
{
	private static final float[] modelLocalX;
	private static final float[] modelLocalY;
	private static final float[] modelLocalZ;

	private final int[] modelLocalXI;
	private final int[] modelLocalYI;
	private final int[] modelLocalZI;

	static
	{
		modelLocalX = com.vr.FacePrioritySorter.modelLocalX;
		modelLocalY = com.vr.FacePrioritySorter.modelLocalY;
		modelLocalZ = com.vr.FacePrioritySorter.modelLocalZ;
	}

	private final RenderCallbackManager renderCallbackManager;
	private int basex, basez, rid, level;

	SceneUploader2(RenderCallbackManager renderCallbackManager)
	{
		this.renderCallbackManager = renderCallbackManager;
		modelLocalXI = new int[com.vr.FacePrioritySorter.MAX_VERTEX_COUNT];
		modelLocalYI = new int[com.vr.FacePrioritySorter.MAX_VERTEX_COUNT];
		modelLocalZI = new int[com.vr.FacePrioritySorter.MAX_VERTEX_COUNT];
	}

	void zoneSize(Scene scene, com.vr.Zone zone, int mzx, int mzz)
	{
		Tile[][][] tiles = scene.getExtendedTiles();

		for (int z = 3; z >= 0; --z)
		{
			for (int xoff = 0; xoff < 8; ++xoff)
			{
				for (int zoff = 0; zoff < 8; ++zoff)
				{
					Tile t = tiles[z][(mzx << 3) + xoff][(mzz << 3) + zoff];
					if (t != null)
					{
						zoneSize(zone, t);
					}
				}
			}
		}
	}

	void uploadZone(Scene scene, com.vr.Zone zone, int mzx, int mzz)
	{
		int[][][] roofs = scene.getRoofs();
		Set<Integer> roofIds = new HashSet<>();

		var vb = zone.vboO != null ? new com.vr.GpuIntBuffer2(zone.vboO.vb) : null;
		var ab = zone.vboA != null ? new com.vr.GpuIntBuffer2(zone.vboA.vb) : null;

		for (int level = 0; level <= 3; ++level)
		{
			for (int xoff = 0; xoff < 8; ++xoff)
			{
				for (int zoff = 0; zoff < 8; ++zoff)
				{
					int rid = roofs[level][(mzx << 3) + xoff][(mzz << 3) + zoff];
					if (rid > 0)
					{
						roofIds.add(rid);
					}
				}
			}
		}

		zone.rids = new int[4][roofIds.size()];
		zone.roofStart = new int[4][roofIds.size()];
		zone.roofEnd = new int[4][roofIds.size()];

		for (int level = 0; level <= 3; ++level)
		{
			this.level = level;

			if (level == 0)
			{
				uploadZoneLevel(scene, zone, mzx, mzz, level, false, roofIds, vb, ab);
				uploadZoneLevel(scene, zone, mzx, mzz, level, true, roofIds, vb, ab);
				uploadZoneLevel(scene, zone, mzx, mzz, 1, true, roofIds, vb, ab);
				uploadZoneLevel(scene, zone, mzx, mzz, 2, true, roofIds, vb, ab);
				uploadZoneLevel(scene, zone, mzx, mzz, 3, true, roofIds, vb, ab);
			}
			else
			{
				uploadZoneLevel(scene, zone, mzx, mzz, level, false, roofIds, vb, ab);
			}

			if (zone.vboO != null)
			{
				int pos = zone.vboO.vb.position();
				zone.levelOffsets[level] = pos;
			}
		}
	}

	private void uploadZoneLevel(Scene scene, com.vr.Zone zone, int mzx, int mzz, int level, boolean visbelow, Set<Integer> roofIds, com.vr.GpuIntBuffer2 vb, com.vr.GpuIntBuffer2 ab)
	{
		int ridx = 0;

		// upload the roofs and save their positions
		for (int id : roofIds)
		{
			int pos = zone.vboO != null ? zone.vboO.vb.position() : 0;

			uploadZoneLevelRoof(scene, zone, mzx, mzz, level, id, visbelow, vb, ab);

			int endpos = zone.vboO != null ? zone.vboO.vb.position() : 0;

			if (endpos > pos)
			{
				zone.rids[level][ridx] = id;
				zone.roofStart[level][ridx] = pos;
				zone.roofEnd[level][ridx] = endpos;
				++ridx;
			}
		}

		// upload everything else
		uploadZoneLevelRoof(scene, zone, mzx, mzz, level, 0, visbelow, vb, ab);
	}

	private void uploadZoneLevelRoof(Scene scene, com.vr.Zone zone, int mzx, int mzz, int level, int roofId, boolean visbelow, com.vr.GpuIntBuffer2 vb, com.vr.GpuIntBuffer2 ab)
	{
		byte[][][] settings = scene.getExtendedTileSettings();
		int[][][] roofs = scene.getRoofs();
		Tile[][][] tiles = scene.getExtendedTiles();

		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? VRPlugin2.SCENE_OFFSET >> 3 : 0;
		this.basex = (mzx - offset) << 10;
		this.basez = (mzz - offset) << 10;

		for (int xoff = 0; xoff < 8; ++xoff)
		{
			for (int zoff = 0; zoff < 8; ++zoff)
			{
				int msx = (mzx << 3) + xoff;
				int msz = (mzz << 3) + zoff;

				boolean isbridge = (settings[1][msx][msz] & Constants.TILE_FLAG_BRIDGE) != 0;
				int maplevel = level;
				if (isbridge)
				{
					++maplevel;
				}

				boolean isvisbelow = maplevel <= 3 && (settings[maplevel][msx][msz] & Constants.TILE_FLAG_VIS_BELOW) != 0;
				if (isvisbelow != visbelow)
				{
					continue;
				}

				int rid;
				if (isvisbelow || maplevel == 0)
				{
					rid = 0;
				}
				else
				{
					rid = roofs[maplevel - 1][msx][msz];
				}

				if (rid == roofId)
				{
					Tile t = tiles[level][msx][msz];
					if (t != null)
					{
						this.rid = rid;
						uploadZoneTile(scene, zone, t, vb, ab);
					}
				}
			}
		}
	}

	private void zoneSize(com.vr.Zone z, Tile t)
	{
		SceneTilePaint paint = t.getSceneTilePaint();
		if (paint != null)
		{
			z.sizeO += 2;
		}

		SceneTileModel model = t.getSceneTileModel();
		if (model != null)
		{
			z.sizeO += model.getFaceX().length;
		}

		WallObject wallObject = t.getWallObject();
		if (wallObject != null)
		{
			zoneRenderableSize(z, wallObject.getRenderable1());
			zoneRenderableSize(z, wallObject.getRenderable2());
		}

		DecorativeObject decorativeObject = t.getDecorativeObject();
		if (decorativeObject != null)
		{
			zoneRenderableSize(z, decorativeObject.getRenderable());
			zoneRenderableSize(z, decorativeObject.getRenderable2());
		}

		GroundObject groundObject = t.getGroundObject();
		if (groundObject != null)
		{
			zoneRenderableSize(z, groundObject.getRenderable());
		}

		GameObject[] gameObjects = t.getGameObjects();
		for (GameObject gameObject : gameObjects)
		{
			if (gameObject == null)
			{
				continue;
			}

			if (!gameObject.getSceneMinLocation().equals(t.getSceneLocation()))
			{
				continue;
			}

			Renderable renderable = gameObject.getRenderable();
			zoneRenderableSize(z, renderable);
		}

		Tile bridge = t.getBridge();
		if (bridge != null)
		{
			zoneSize(z, bridge);
		}
	}

	private int uploadZoneTile(Scene scene, com.vr.Zone zone, Tile t, com.vr.GpuIntBuffer2 vertexBuffer, com.vr.GpuIntBuffer2 ab)
	{
		int len = 0;
		boolean drawTile = renderCallbackManager.drawTile(scene, t);

		SceneTilePaint paint = t.getSceneTilePaint();
		if (paint != null && drawTile)
		{
			Point tilePoint = t.getSceneLocation();
			len = upload(scene, paint,
				t.getRenderLevel(), tilePoint.getX(), tilePoint.getY(),
				vertexBuffer,
				tilePoint.getX() * 128 - basex, tilePoint.getY() * 128 - basez
			);
		}

		SceneTileModel model = t.getSceneTileModel();
		if (model != null && drawTile)
		{
			Point tilePoint = t.getSceneLocation();
			len += upload(model, tilePoint.getX() << 7, tilePoint.getY() << 7, vertexBuffer);
		}

		WallObject wallObject = t.getWallObject();
		if (wallObject != null && renderCallbackManager.drawObject(scene, wallObject))
		{
			Renderable renderable1 = wallObject.getRenderable1();
			uploadZoneRenderable(renderable1, zone, 0, wallObject.getX(), wallObject.getZ(), wallObject.getY(), -1, -1, -1, -1, wallObject.getId(), vertexBuffer, ab);

			Renderable renderable2 = wallObject.getRenderable2();
			uploadZoneRenderable(renderable2, zone, 0, wallObject.getX(), wallObject.getZ(), wallObject.getY(), -1, -1, -1, -1, wallObject.getId(), vertexBuffer, ab);
		}

		DecorativeObject decorativeObject = t.getDecorativeObject();
		if (decorativeObject != null && renderCallbackManager.drawObject(scene, decorativeObject))
		{
			Renderable renderable = decorativeObject.getRenderable();
			uploadZoneRenderable(renderable, zone, 0, decorativeObject.getX() + decorativeObject.getXOffset(), decorativeObject.getZ(), decorativeObject.getY() + decorativeObject.getYOffset(), -1, -1, -1, -1, decorativeObject.getId(), vertexBuffer, ab);

			Renderable renderable2 = decorativeObject.getRenderable2();
			uploadZoneRenderable(renderable2, zone, 0, decorativeObject.getX() + decorativeObject.getXOffset2(), decorativeObject.getZ(), decorativeObject.getY() + decorativeObject.getYOffset2(), -1, -1, -1, -1, decorativeObject.getId(), vertexBuffer, ab);
		}

		GroundObject groundObject = t.getGroundObject();
		if (groundObject != null && renderCallbackManager.drawObject(scene, groundObject))
		{
			Renderable renderable = groundObject.getRenderable();
			uploadZoneRenderable(renderable, zone, 0, groundObject.getX(), groundObject.getZ(), groundObject.getY(),
				-1, -1, -1, -1,
				groundObject.getId(),
				vertexBuffer, ab);
		}

		GameObject[] gameObjects = t.getGameObjects();
		for (GameObject gameObject : gameObjects)
		{
			if (gameObject == null)
			{
				continue;
			}

			Point min = gameObject.getSceneMinLocation(), max = gameObject.getSceneMaxLocation();

			if (!min.equals(t.getSceneLocation()))
			{
				continue;
			}

			if (!renderCallbackManager.drawObject(scene, gameObject))
			{
				continue;
			}

			Renderable renderable = gameObject.getRenderable();
			uploadZoneRenderable(renderable, zone, gameObject.getModelOrientation(), gameObject.getX(), gameObject.getZ(), gameObject.getY(),
				min.getX(), min.getY(), max.getX(), max.getY(),
				gameObject.getId(),
				vertexBuffer, ab);
		}

		Tile bridge = t.getBridge();
		if (bridge != null)
		{
			len += uploadZoneTile(scene, zone, bridge, vertexBuffer, ab);
		}

		return len;
	}

	private void zoneRenderableSize(com.vr.Zone z, Renderable r)
	{
		Model m = null;
		if (r instanceof Model)
		{
			m = (Model) r;
		}
		else if (r instanceof DynamicObject)
		{
			m = ((DynamicObject) r).getModelZbuf();
		}
		if (m == null)
		{
			return;
		}

		byte[] transparencies = m.getFaceTransparencies();
		int faceCount = m.getFaceCount();
		if (transparencies != null)
		{
			for (int face = 0; face < faceCount; ++face)
			{
				boolean alpha = transparencies[face] != 0;
				if (alpha)
				{
					z.sizeA++;
				}
				else
				{
					z.sizeO++;
				}
			}
			return;
		}
		z.sizeO += faceCount;
	}

	private void uploadZoneRenderable(Renderable r, com.vr.Zone zone, int orient, int x, int y, int z, int lx, int lz, int ux, int uz, int id, com.vr.GpuIntBuffer2 vb, com.vr.GpuIntBuffer2 ab)
	{
		int pos = zone.vboA != null ? zone.vboA.vb.position() : 0;
		Model model = null;
		if (r instanceof Model)
		{
			model = (Model) r;
			uploadStaticModel(model, orient, x - basex, y, z - basez, vb, ab);
		}
		else if (r instanceof DynamicObject)
		{
			model = ((DynamicObject) r).getModelZbuf();
			if (model != null)
			{
				uploadStaticModel(model, orient, x - basex, y, z - basez, vb, ab);
			}
		}
		int endpos = zone.vboA != null ? zone.vboA.vb.position() : 0;
		if (endpos > pos)
		{
			assert model != null;
			if (lx > -1)
			{
				lx -= basex >> 7;
				lz -= basez >> 7;
				ux -= basex >> 7;
				uz -= basez >> 7;
				assert lx >= 0 : lx;
				assert lz >= 0 : lz;
				assert ux < 25 : ux; // largest object?
				assert uz < 25 : uz;
			}
			zone.addAlphaModel(zone.glVaoA, model, pos, endpos,
				x - basex, y, z - basez,
				lx, lz, ux, uz,
				rid, level, id);
		}
	}

	private int upload(Scene scene, SceneTilePaint tile, int tileZ, int tileX, int tileY, com.vr.GpuIntBuffer2 vertexBuffer, int lx, int lz)
	{
		tileX += scene.getWorldViewId() == WorldView.TOPLEVEL ? VRPlugin2.SCENE_OFFSET : 0;
		tileY += scene.getWorldViewId() == WorldView.TOPLEVEL ? VRPlugin2.SCENE_OFFSET : 0;

		final int[][][] tileHeights = scene.getTileHeights();
		final int swHeight = tileHeights[tileZ][tileX][tileY];
		final int seHeight = tileHeights[tileZ][tileX + 1][tileY];
		final int neHeight = tileHeights[tileZ][tileX + 1][tileY + 1];
		final int nwHeight = tileHeights[tileZ][tileX][tileY + 1];

		final int swColor = tile.getSwColor();
		final int seColor = tile.getSeColor();
		final int neColor = tile.getNeColor();
		final int nwColor = tile.getNwColor();

		if (neColor == 12345678)
		{
			return 0;
		}

		// 0,0
		final int lx0 = lx;
		final int ly0 = swHeight;
		final int lz0 = lz;
		final int hsl0 = swColor;

		// 1,0
		final int lx1 = lx + Perspective.LOCAL_TILE_SIZE;
		final int ly1 = seHeight;
		final int lz1 = lz;
		final int hsl1 = seColor;

		// 1,1
		final int lx2 = lx + Perspective.LOCAL_TILE_SIZE;
		final int ly2 = neHeight;
		final int lz2 = lz + Perspective.LOCAL_TILE_SIZE;
		final int hsl2 = neColor;

		// 0,1
		final int lx3 = lx;
		final int ly3 = nwHeight;
		final int lz3 = lz + Perspective.LOCAL_TILE_SIZE;
		final int hsl3 = nwColor;

		int tex = tile.getTexture() + 1;

		vertexBuffer.put22224(lx2, ly2, lz2, hsl2);
		vertexBuffer.put2222(tex, 256, 256, 0);

		vertexBuffer.put22224(lx3, ly3, lz3, hsl3);
		vertexBuffer.put2222(tex, 0, 256, 0);

		vertexBuffer.put22224(lx1, ly1, lz1, hsl1);
		vertexBuffer.put2222(tex, 256, 0, 0);

		vertexBuffer.put22224(lx0, ly0, lz0, hsl0);
		vertexBuffer.put2222(tex, 0, 0, 0);

		vertexBuffer.put22224(lx1, ly1, lz1, hsl1);
		vertexBuffer.put2222(tex, 256, 0, 0);

		vertexBuffer.put22224(lx3, ly3, lz3, hsl3);
		vertexBuffer.put2222(tex, 0, 256, 0);

		return 6;
	}

	private int upload(SceneTileModel sceneTileModel, int lx, int lz, com.vr.GpuIntBuffer2 vertexBuffer)
	{
		final int[] faceX = sceneTileModel.getFaceX();
		final int[] faceY = sceneTileModel.getFaceY();
		final int[] faceZ = sceneTileModel.getFaceZ();

		final int[] vertexX = sceneTileModel.getVertexX();
		final int[] vertexY = sceneTileModel.getVertexY();
		final int[] vertexZ = sceneTileModel.getVertexZ();

		final int[] triangleColorA = sceneTileModel.getTriangleColorA();
		final int[] triangleColorB = sceneTileModel.getTriangleColorB();
		final int[] triangleColorC = sceneTileModel.getTriangleColorC();

		final int[] triangleTextures = sceneTileModel.getTriangleTextureId();

		final int faceCount = faceX.length;

		int cnt = 0;
		for (int i = 0; i < faceCount; ++i)
		{
			final int vertex0 = faceX[i];
			final int vertex1 = faceY[i];
			final int vertex2 = faceZ[i];

			final int hsl0 = triangleColorA[i];
			final int hsl1 = triangleColorB[i];
			final int hsl2 = triangleColorC[i];

			if (hsl0 == 12345678)
			{
				continue;
			}

			cnt += 3;

			// vertexes are stored in scene local, convert to tile local
			int lx0 = vertexX[vertex0] - basex;
			int ly0 = vertexY[vertex0];
			int lz0 = vertexZ[vertex0] - basez;

			int lx1 = vertexX[vertex1] - basex;
			int ly1 = vertexY[vertex1];
			int lz1 = vertexZ[vertex1] - basez;

			int lx2 = vertexX[vertex2] - basex;
			int ly2 = vertexY[vertex2];
			int lz2 = vertexZ[vertex2] - basez;

			int tex = triangleTextures != null ? triangleTextures[i] + 1 : 0;
			vertexBuffer.put22224(lx0, ly0, lz0, hsl0);
			vertexBuffer.put2222(tex, (int) ((vertexX[vertex0] - lx) * 2f), (int) ((vertexZ[vertex0] - lz) * 2f), 0);

			vertexBuffer.put22224(lx1, ly1, lz1, hsl1);
			vertexBuffer.put2222(tex, (int) ((vertexX[vertex1] - lx) * 2f), (int) ((vertexZ[vertex1] - lz) * 2f), 0);

			vertexBuffer.put22224(lx2, ly2, lz2, hsl2);
			vertexBuffer.put2222(tex, (int) ((vertexX[vertex2] - lx) * 2f), (int) ((vertexZ[vertex2] - lz) * 2f), 0);
		}

		return cnt;
	}

	// scene upload
	private int uploadStaticModel(Model model, int orient, int x, int y, int z, com.vr.GpuIntBuffer2 vb, com.vr.GpuIntBuffer2 ab)
	{
		final int vertexCount = model.getVerticesCount();
		final int triangleCount = model.getFaceCount();

		final float[] vertexX = model.getVerticesX();
		final float[] vertexY = model.getVerticesY();
		final float[] vertexZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		final short[] faceTextures = model.getFaceTextures();

		final byte[] transparencies = model.getFaceTransparencies();
		final byte[] bias = model.getFaceBias();

		int orientSin = 0;
		int orientCos = 0;
		if (orient != 0)
		{
			orientSin = Perspective.SINE[orient];
			orientCos = Perspective.COSINE[orient];
		}

		for (int v = 0; v < vertexCount; ++v)
		{
			int vx = (int) vertexX[v];
			int vy = (int) vertexY[v];
			int vz = (int) vertexZ[v];

			if (orient != 0)
			{
				int x0 = vx;
				vx = vz * orientSin + x0 * orientCos >> 16;
				vz = vz * orientCos - x0 * orientSin >> 16;
			}

			vx += x;
			vy += y;
			vz += z;

			modelLocalXI[v] = vx;
			modelLocalYI[v] = vy;
			modelLocalZI[v] = vz;
		}

		int len = 0;
		for (int face = 0; face < triangleCount; ++face)
		{
			int color1 = color1s[face];
			int color2 = color2s[face];
			int color3 = color3s[face];

			boolean alpha = (transparencies != null && transparencies[face] != 0);

			if (color3 == -1)
			{
				color2 = color3 = color1;
			}
			else if (color3 == -2)
			{
				continue;
			}

			int triangleA = indices1[face];
			int triangleB = indices2[face];
			int triangleC = indices3[face];

			int vx1 = modelLocalXI[triangleA];
			int vy1 = modelLocalYI[triangleA];
			int vz1 = modelLocalZI[triangleA];

			int vx2 = modelLocalXI[triangleB];
			int vy2 = modelLocalYI[triangleB];
			int vz2 = modelLocalZI[triangleB];

			int vx3 = modelLocalXI[triangleC];
			int vy3 = modelLocalYI[triangleC];
			int vz3 = modelLocalZI[triangleC];

			computeFaceUvs(model, face);

			int su0 = (int) (u0 * 256f);
			int sv0 = (int) (v0 * 256f);

			int su1 = (int) (u1 * 256f);
			int sv1 = (int) (v1 * 256f);

			int su2 = (int) (u2 * 256f);
			int sv2 = (int) (v2 * 256f);

			int alphaBias = 0;
			alphaBias |= transparencies != null ? (transparencies[face] & 0xff) << 24 : 0;
			alphaBias |= bias != null ? (bias[face] & 0xff) << 16 : 0;
			int texture = faceTextures != null ? faceTextures[face] + 1 : 0;
			com.vr.GpuIntBuffer2 buf = alpha ? ab : vb;

			buf.put22224(vx1, vy1, vz1, alphaBias | color1);
			buf.put2222(texture, su0, sv0, 0);

			buf.put22224(vx2, vy2, vz2, alphaBias | color2);
			buf.put2222(texture, su1, sv1, 0);

			buf.put22224(vx3, vy3, vz3, alphaBias | color3);
			buf.put2222(texture, su2, sv2, 0);

			len += 3;
		}

		return len;
	}

	// temp draw
	int uploadTempModel(Model model, int orientation, int x, int y, int z, IntBuffer opaqueBuffer)
	{
		final int triangleCount = model.getFaceCount();
		final int vertexCount = model.getVerticesCount();

		final float[] verticesX = model.getVerticesX();
		final float[] verticesY = model.getVerticesY();
		final float[] verticesZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		final short[] faceTextures = model.getFaceTextures();

		final byte[] bias = model.getFaceBias();

		final byte overrideAmount = model.getOverrideAmount();
		final byte overrideHue = model.getOverrideHue();
		final byte overrideSat = model.getOverrideSaturation();
		final byte overrideLum = model.getOverrideLuminance();

		float orientSine = 0;
		float orientCosine = 0;
		if (orientation != 0)
		{
			orientSine = Perspective.SINE[orientation] / 65536f;
			orientCosine = Perspective.COSINE[orientation] / 65536f;
		}

		for (int v = 0; v < vertexCount; ++v)
		{
			float vertexX = verticesX[v];
			float vertexY = verticesY[v];
			float vertexZ = verticesZ[v];

			if (orientation != 0)
			{
				float x0 = vertexX;
				vertexX = vertexZ * orientSine + x0 * orientCosine;
				vertexZ = vertexZ * orientCosine - x0 * orientSine;
			}

			vertexX += x;
			vertexY += y;
			vertexZ += z;

			modelLocalX[v] = vertexX;
			modelLocalY[v] = vertexY;
			modelLocalZ[v] = vertexZ;
		}

		int len = 0;
		for (int face = 0; face < triangleCount; ++face)
		{
			int color1 = color1s[face];
			int color2 = color2s[face];
			int color3 = color3s[face];

			if (color3 == -1)
			{
				color2 = color3 = color1;
			}
			else if (color3 == -2)
			{
				continue;
			}

			// HSL override is not applied to textured faces
			if (faceTextures == null || faceTextures[face] == -1)
			{
				if (overrideAmount > 0)
				{
					color1 = interpolateHSL(color1, overrideHue, overrideSat, overrideLum, overrideAmount);
					color2 = interpolateHSL(color2, overrideHue, overrideSat, overrideLum, overrideAmount);
					color3 = interpolateHSL(color3, overrideHue, overrideSat, overrideLum, overrideAmount);
				}
			}

			int triangleA = indices1[face];
			int triangleB = indices2[face];
			int triangleC = indices3[face];

			float vx1 = modelLocalX[triangleA];
			float vy1 = modelLocalY[triangleA];
			float vz1 = modelLocalZ[triangleA];

			float vx2 = modelLocalX[triangleB];
			float vy2 = modelLocalY[triangleB];
			float vz2 = modelLocalZ[triangleB];

			float vx3 = modelLocalX[triangleC];
			float vy3 = modelLocalY[triangleC];
			float vz3 = modelLocalZ[triangleC];

			computeFaceUvs(model, face);

			int su0 = (int) (u0 * 256f);
			int sv0 = (int) (v0 * 256f);

			int su1 = (int) (u1 * 256f);
			int sv1 = (int) (v1 * 256f);

			int su2 = (int) (u2 * 256f);
			int sv2 = (int) (v2 * 256f);

			int alphaBias = 0;
			alphaBias |= bias != null ? (bias[face] & 0xff) << 16 : 0;
			int texture = faceTextures != null ? faceTextures[face] + 1 : 0;

			putfff4(opaqueBuffer, vx1, vy1, vz1, alphaBias | color1);
			put2222(opaqueBuffer, texture, su0, sv0, 0);

			putfff4(opaqueBuffer, vx2, vy2, vz2, alphaBias | color2);
			put2222(opaqueBuffer, texture, su1, sv1, 0);

			putfff4(opaqueBuffer, vx3, vy3, vz3, alphaBias | color3);
			put2222(opaqueBuffer, texture, su2, sv2, 0);

			len += 3;
		}

		return len;
	}

	static void put2222(IntBuffer vb, int x, int y, int z, int w)
	{
		vb.put(((y & 0xffff) << 16) | (x & 0xffff));
		vb.put(((w & 0xffff) << 16) | (z & 0xffff));
	}

	static void putfff4(IntBuffer vb, float x, float y, float z, int w)
	{
		vb.put(Float.floatToIntBits(x));
		vb.put(Float.floatToIntBits(y));
		vb.put(Float.floatToIntBits(z));
		vb.put(w);
	}

	static int interpolateHSL(int hsl, byte hue2, byte sat2, byte lum2, byte lerp)
	{
		int hue = hsl >> 10 & 63;
		int sat = hsl >> 7 & 7;
		int lum = hsl & 127;
		int var9 = lerp & 255;
		if (hue2 != -1)
		{
			hue += var9 * (hue2 - hue) >> 7;
		}

		if (sat2 != -1)
		{
			sat += var9 * (sat2 - sat) >> 7;
		}

		if (lum2 != -1)
		{
			lum += var9 * (lum2 - lum) >> 7;
		}

		return (hue << 10 | sat << 7 | lum) & 65535;
	}

	float u0, u1, u2, v0, v1, v2;

	void computeFaceUvs(Model model, int face)
	{
		final float[] vertexX = model.getVerticesX();
		final float[] vertexY = model.getVerticesY();
		final float[] vertexZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final byte[] textureFaces = model.getTextureFaces();
		final int[] texIndices1 = model.getTexIndices1();
		final int[] texIndices2 = model.getTexIndices2();
		final int[] texIndices3 = model.getTexIndices3();

		if (textureFaces != null && textureFaces[face] != -1)
		{
			final int triangleA = indices1[face];
			final int triangleB = indices2[face];
			final int triangleC = indices3[face];

			int tfaceIdx = textureFaces[face] & 0xff;
			int texA = texIndices1[tfaceIdx];
			int texB = texIndices2[tfaceIdx];
			int texC = texIndices3[tfaceIdx];

			// v1 = vertex[texA]
			float v1x = vertexX[texA];
			float v1y = vertexY[texA];
			float v1z = vertexZ[texA];
			// v2 = vertex[texB] - v1
			float v2x = vertexX[texB] - v1x;
			float v2y = vertexY[texB] - v1y;
			float v2z = vertexZ[texB] - v1z;
			// v3 = vertex[texC] - v1
			float v3x = vertexX[texC] - v1x;
			float v3y = vertexY[texC] - v1y;
			float v3z = vertexZ[texC] - v1z;

			// v4 = vertex[triangleA] - v1
			float v4x = vertexX[triangleA] - v1x;
			float v4y = vertexY[triangleA] - v1y;
			float v4z = vertexZ[triangleA] - v1z;
			// v5 = vertex[triangleB] - v1
			float v5x = vertexX[triangleB] - v1x;
			float v5y = vertexY[triangleB] - v1y;
			float v5z = vertexZ[triangleB] - v1z;
			// v6 = vertex[triangleC] - v1
			float v6x = vertexX[triangleC] - v1x;
			float v6y = vertexY[triangleC] - v1y;
			float v6z = vertexZ[triangleC] - v1z;

			// v7 = v2 x v3
			float v7x = v2y * v3z - v2z * v3y;
			float v7y = v2z * v3x - v2x * v3z;
			float v7z = v2x * v3y - v2y * v3x;

			// v8 = v3 x v7
			float v8x = v3y * v7z - v3z * v7y;
			float v8y = v3z * v7x - v3x * v7z;
			float v8z = v3x * v7y - v3y * v7x;

			// f = 1 / (v8 ⋅ v2)
			float f = 1.0F / (v8x * v2x + v8y * v2y + v8z * v2z);

			// u0 = (v8 ⋅ v4) * f
			u0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// u1 = (v8 ⋅ v5) * f
			u1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// u2 = (v8 ⋅ v6) * f
			u2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;

			// v8 = v2 x v7
			v8x = v2y * v7z - v2z * v7y;
			v8y = v2z * v7x - v2x * v7z;
			v8z = v2x * v7y - v2y * v7x;

			// f = 1 / (v8 ⋅ v3)
			f = 1.0F / (v8x * v3x + v8y * v3y + v8z * v3z);

			// v0 = (v8 ⋅ v4) * f
			v0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// v1 = (v8 ⋅ v5) * f
			v1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// v2 = (v8 ⋅ v6) * f
			v2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;
		}
		else
		{
			// Without a texture face, the client assigns tex = triangle, but the resulting
			// calculations can be reduced:
			//
			// v1 = vertex[texA]
			// v2 = vertex[texB] - v1
			// v3 = vertex[texC] - v1
			//
			// v4 = 0
			// v5 = v2
			// v6 = v3
			//
			// v7 = v2 x v3
			//
			// v8 = v3 x v7
			// u0 = (v8 . v4) / (v8 . v2) // 0 because v4 is 0
			// u1 = (v8 . v5) / (v8 . v2) // 1 because v5=v2
			// u2 = (v8 . v6) / (v8 . v2) // 0 because v8 is perpendicular to v3/v6
			//
			// v8 = v2 x v7
			// v0 = (v8 . v4) / (v8 ⋅ v3) // 0 because v4 is 0
			// v1 = (v8 . v5) / (v8 ⋅ v3) // 0 because v8 is perpendicular to v5/v2
			// v2 = (v8 . v6) / (v8 ⋅ v3) // 1 because v6=v3

			u0 = 0f;
			v0 = 0f;

			u1 = 1f;
			v1 = 0f;

			u2 = 0f;
			v2 = 1f;
		}
	}

	public int pushModelOutlinePhase1(Projection proj, Model model, int orientation, int x, int y, int z, GpuIntBuffer vertexBuffer){
		return pushModelOutline(proj, model, orientation, x, y, z, vertexBuffer, 0.0f, 0);
	}

	public int pushModelOutlinePhase2(Projection proj, Model model, int orientation, int x, int y, int z, GpuIntBuffer vertexBuffer, int color){
		return pushModelOutline(proj, model, orientation, x, y, z, vertexBuffer, 5.f, color);
	}

	public int pushModelOutlineCombined(Projection proj, Model model, int orientation, int x, int y, int z, GpuIntBuffer vertexBuffer, int color){
		return pushModelOutlinePhase1(proj, model, orientation, x, y, z, vertexBuffer)+
				pushModelOutlinePhase2(proj, model, orientation, x, y, z, vertexBuffer, color);
	}

	public int pushModelOutline(Projection proj, Model model, int orientation, int x, int y, int z, GpuIntBuffer vertexBuffer, float scale, int color)
	{
		/*if (model.getUnskewedModel() != null)
		{
			//System.out.println("SKEWED.");
			model = model.getUnskewedModel();
		}*/

		final int triangleCount = Math.min(model.getFaceCount(), VRPlugin.MAX_TRIANGLE);

		vertexBuffer.ensureCapacity(triangleCount * 12);

		final float[] verticesX2 = model.getVerticesX();
		final float[] verticesY2 = model.getVerticesY();
		final float[] verticesZ2 = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final float[] normalsX = new float[verticesX2.length];
		final float[] normalsY = new float[verticesX2.length];
		final float[] normalsZ = new float[verticesX2.length];

		//short[] faceTextures = model.getFaceTextures();
		//byte[] faceTransparencies = model.getFaceTransparencies();

		for(int i = 0; i < normalsX.length; i++) {
			normalsX[i] = 0;
			normalsY[i] = 0;
			normalsZ[i] = 0;
		}

		for(int i = 0; i < indices1.length; i++) {
			Vector3f vec1 = new Vector3f(verticesX2[indices1[i]],verticesY2[indices1[i]],verticesZ2[indices1[i]]);
			Vector3f vec2 = new Vector3f(verticesX2[indices2[i]],verticesY2[indices2[i]],verticesZ2[indices2[i]]);
			Vector3f vec3 = new Vector3f(verticesX2[indices3[i]],verticesY2[indices3[i]],verticesZ2[indices3[i]]);
			Vector3f vec4 = vec2.sub(vec1).cross(vec3.sub(vec1));
			normalsX[indices1[i]] += vec4.x; normalsX[indices2[i]] += vec4.x; normalsX[indices3[i]] += vec4.x;
			normalsY[indices1[i]] += vec4.y; normalsY[indices2[i]] += vec4.y; normalsY[indices3[i]] += vec4.y;
			normalsZ[indices1[i]] += vec4.z; normalsZ[indices2[i]] += vec4.z; normalsZ[indices3[i]] += vec4.z;
		}
		for(int i = 0; i < normalsX.length; i++) {
			double len = Math.sqrt( Math.pow(normalsX[i],2.0)+Math.pow(normalsY[i],2.0)+Math.pow(normalsZ[i],2.0));
			if(len != 0.0f) {
				normalsX[i] /= len;
				normalsY[i] /= len;
				normalsZ[i] /= len;
			}
		}

		final float[] verticesX = new float[verticesX2.length];
		final float[] verticesY = new float[verticesX2.length];
		final float[] verticesZ = new float[verticesX2.length];

		for(int i = 0; i < normalsX.length; i++){
			verticesX[i] = verticesX2[i]+normalsX[i]*scale;
			verticesY[i] = verticesY2[i]+normalsY[i]*scale;
			verticesZ[i] = verticesZ2[i]+normalsZ[i]*scale;
		}

		//final int centerX = client.getCenterX();
		//final int centerY = client.getCenterY();
		//final int zoom = client.get3dZoom();

		int orientSine = 0;
		int orientCosine = 0;
		if (orientation != 0)
		{
			orientSine = Perspective.SINE[orientation];
			orientCosine = Perspective.COSINE[orientation];
		}

		//float[] p = proj.project(x, y, z);
		//int zero = (int) p[2];

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		int len = 0;
		for (int face = 0; face < triangleCount; ++face) {
			//if ((faceTextures == null || faceTextures[face] == -1) && packAlphaPriority(faceTextures, faceTransparencies, null, face) != 0) continue;

			int triangleA = indices1[face];
			int triangleB = indices2[face];
			int triangleC = indices3[face];

			for(int ind : new int[]{triangleA, triangleB, triangleC}) {

				int vertexX = (int) ((verticesX[ind]));
				int vertexY = (int) ((verticesY[ind]));
				int vertexZ = (int) ((verticesZ[ind]));

				if (orientation != 0) {
					int i = vertexZ * orientSine + vertexX * orientCosine >> 16;
					vertexZ = vertexZ * orientCosine - vertexX * orientSine >> 16;
					vertexX = i;
				}

				// move to local position
				vertexX += x;
				vertexY += y;
				vertexZ += z;

				vertexBuffer.put(vertexX, vertexY, vertexZ, color);
				len += 1;
			}
		}

		return len;
	}
}
