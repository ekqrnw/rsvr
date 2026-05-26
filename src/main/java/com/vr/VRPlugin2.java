/*
 * Copyright (c) 2024, Ekqrnw <ekqrnw@gmail.com>
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;
import com.google.inject.Provides;
import com.vr.config.UIScalingMode2;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import com.vr.config.AntiAliasingMode;
import com.vr.template.Template;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.OSType;
import net.runelite.rlawt.AWTContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.openxr.*;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGB10_A2;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT32;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_MAJOR_VERSION;
import static org.lwjgl.opengl.GL30.GL_MINOR_VERSION;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.GL45C.GL_ZERO_TO_ONE;
import static org.lwjgl.opengl.GL45C.glClipControl;
import static org.lwjgl.openxr.EXTDebugUtils.*;
import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.openxr.MNDXEGLEnable.XR_MNDX_EGL_ENABLE_EXTENSION_NAME;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.openxr.XR10.XR_SESSION_STATE_LOSS_PENDING;
import static org.lwjgl.system.MemoryStack.stackMalloc;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.MemoryUtil.NULL;

@PluginDescriptor(
	name = "VR",
	description = "Renders to a VR headset",
	tags = {"fog", "draw distance", "resources/vr", "oculus"},
	loadInSafeMode = false
)
@Slf4j
public class VRPlugin2 extends Plugin implements DrawCallbacks
{
	long window;

	//XR globals
	//Init
	XrInstance                     xrInstance;
	long                           systemID;
	XrSession                      xrSession;
	boolean                        missingXrDebug;
	boolean                        useEglGraphicsBinding;
	XrDebugUtilsMessengerEXT       xrDebugMessenger;
	XrSpace                        xrAppSpace;  //The real world space in which the program runs

	XrSpace                        xrHeadSpace;

	XrSpace                        leftHandSpace;

	XrSpace                        rightHandSpace;

	XrActionSet                    xrActionSet;

	XrAction                       rightClick;
	XrAction                       leftClick;
	XrAction                       middleClick;

	XrAction                       aButton;
	XrAction                       bButton;

	XrAction                       xButton;
	XrAction                       pose;

	XrPosef                        leftPose;
	XrPosef                        rightPose;

	long                           leftHandPath;
	long                           rightHandPath;

	long                           glColorFormat;
	XrView.Buffer                  views;       //Each view reperesents an eye in the headset with views[0] being left and views[1] being right
	VRPlugin.Swapchain[]                    swapchains;  //One swapchain per view
	XrViewConfigurationView.Buffer viewConfigs;
	int                            viewConfigType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

	private static Matrix4f modelviewMatrix  = new Matrix4f();
	private static Matrix4f projectionMatrix = new Matrix4f();
	private static Matrix4f viewMatrix       = new Matrix4f();

	private static Matrix4f handMatrix       = new Matrix4f();

	private static Matrix4f mapMatrix       = new Matrix4f();

	private static Matrix4f cursorMatrix       = new Matrix4f();


	//Runtime
	XrEventDataBuffer eventDataBuffer;
	int               sessionState;
	boolean           sessionRunning;

	//GL globals
	Map<XrSwapchainImageOpenGLKHR, Integer> depthTextures; //Swapchain images only provide a color texture so we have to create depth textures seperatley

	int swapchainFramebuffer;

	static class Swapchain {
		XrSwapchain                      handle;
		int                              width;
		int                              height;
		XrSwapchainImageOpenGLKHR.Buffer images;
	}

	static final int MAX_DISTANCE = 184;
	static final int MAX_FOG_DEPTH = 100;
	static final int SCENE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2; // offset for sxy -> msxy
	private static final int UNIFORM_BUFFER_SIZE = 5 * Float.BYTES;
	private static final int NUM_ZONES = Constants.EXTENDED_SCENE_SIZE >> 3;
	private static final int MAX_WORLDVIEWS = 4096;

	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ClientThread clientThread;

	@Inject
	private VRPlugin2Config config;

	@Inject
	private com.vr.TextureManager textureManager;

	@Inject
	private RegionManager regionManager;

	@Inject
	private DrawManager drawManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private Hooks hooks;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	private Canvas canvas;
	private AWTContext awtContext;
	private Callback debugCallback;

	private boolean lwjglInitted = false;
	private GLCapabilities glCapabilities;

	static final com.vr.Shader PROGRAM = new com.vr.Shader()
		.add(GL33C.GL_VERTEX_SHADER, "vert2.glsl")
		.add(GL33C.GL_FRAGMENT_SHADER, "frag2.glsl");

	static final com.vr.Shader UI_PROGRAM = new com.vr.Shader()
		.add(GL33C.GL_VERTEX_SHADER, "vertui.glsl")
		.add(GL33C.GL_FRAGMENT_SHADER, "fragui2.glsl");

	static final com.vr.Shader OUTLINE_PROGRAM = new com.vr.Shader()
			.add(GL43C.GL_VERTEX_SHADER, "vertoutline.glsl")
			.add(GL43C.GL_GEOMETRY_SHADER, "geomoutline2.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fragoutline.glsl");

	static final com.vr.Shader HAND_PROGRAM = new com.vr.Shader()
			.add(GL43C.GL_VERTEX_SHADER, "verthands.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fraghands.glsl");

	static final com.vr.Shader HUD3_PROGRAM = new Shader()
			.add(GL43C.GL_VERTEX_SHADER, "verthud.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fraghud3.glsl");

	static final com.vr.Shader HUD_PROGRAM = new Shader()
			.add(GL43C.GL_VERTEX_SHADER, "verthud.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fraghud.glsl");

	static final com.vr.Shader MENU_PROGRAM = new Shader()
			.add(GL43C.GL_VERTEX_SHADER, "vertmenu.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fragui2.glsl");

	static final com.vr.Shader HINT_PROGRAM = new Shader()
			.add(GL43C.GL_VERTEX_SHADER, "verthint.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fraghud.glsl");

	static final com.vr.Shader HUD2_PROGRAM = new Shader()
			.add(GL43C.GL_VERTEX_SHADER, "verthud2.glsl")
			.add(GL43C.GL_FRAGMENT_SHADER, "fraghud2.glsl");

	static int glProgram;

	static int glOutlineProgram;

	private int glUiProgram;

	private int glHandProgram;

	private int glMenuProgram;

	private int interfaceTexture;
	private int interfacePbo;

	private int menuTexture;
	private int menuPbo;

	private int vaoUiHandle;
	private int vboUiHandle;

	private int vaoMenuHandle;

	private int vaoHandHandle;

	private int vboMenuHandle;

	private int vboHandHandle;

	private int vaoOutlineTemp;



	private int fboScene;
	private boolean sceneFboValid;
	private int rboColorBuffer;
	private int rboDepthBuffer;

	private int textureArrayId;

	private final com.vr.GLBuffer glUniformBuffer = new com.vr.GLBuffer("uniform buffer");

	private int lastCanvasWidth;
	private int lastCanvasHeight;
	private int lastStretchedCanvasWidth;
	private int lastStretchedCanvasHeight;
	private AntiAliasingMode lastAntiAliasingMode;
	private int lastAnisotropicFilteringLevel = -1;

	private com.vr.GpuFloatBuffer2 uniformBuffer;

	private com.vr.GpuIntBuffer outlineVertexBuffer;

	private double cameraX, cameraY, cameraZ;
	private int cameraYaw, cameraPitch;
	private double cameraFpYaw, cameraFpPitch;

	private VAOList vaoO;
	private VAOList vaoA;
	private VAOList vaoPO;

	private com.vr.SceneUploader2 clientUploader, mapUploader;
	private FacePrioritySorter facePrioritySorter;

	static class SceneContext
	{
		final int sizeX, sizeZ;
		Zone[][] zones;

		private int cameraX, cameraY, cameraZ;
		private int minLevel, level, maxLevel;
		private Set<Integer> hideRoofIds;

		SceneContext(int sizeX, int sizeZ)
		{
			this.sizeX = sizeX;
			this.sizeZ = sizeZ;
			zones = new Zone[sizeX][sizeZ];
			for (int x = 0; x < sizeX; ++x)
			{
				for (int z = 0; z < sizeZ; ++z)
				{
					zones[x][z] = new Zone();
				}
			}
		}

		void free()
		{
			for (int x = 0; x < sizeX; ++x)
			{
				for (int z = 0; z < sizeZ; ++z)
				{
					zones[x][z].free();
				}
			}
		}
	}

	SceneContext context(Scene scene)
	{
		int wvid = scene.getWorldViewId();
		if (wvid == WorldView.TOPLEVEL)
		{
			return root;
		}
		return subs[wvid];
	}

	SceneContext context(WorldView wv)
	{
		int wvid = wv.getId();
		if (wvid == WorldView.TOPLEVEL)
		{
			return root;
		}
		return subs[wvid];
	}

	private SceneContext root;
	private SceneContext[] subs;
	private Zone[][] nextZones;
	private Map<Integer, Integer> nextRoofChanges;

	// Uniforms
	private int uniUseFog;
	private int uniFogColor;
	private int uniFogDepth;
	private int uniDrawDistance;
	private int uniExpandedMapLoadingChunks;
	private int uniSmoothBanding;
	private int uniWorldProj;
	private static int uniEntityProj;

	private static int uniOutlineEntityProj;
	static int uniEntityTint;
	private int uniBrightness;
	private int uniTex;
	private int uniTexSourceDimensions;
	private int uniTexTargetDimensions;
	private int uniUiAlphaOverlay;
	private int uniTextures;
	private int uniTextureAnimations;
	private int uniBlockMain;
	private int uniTextureLightMode;
	private int uniTick;
	private int uniColorblindIntensity;
	private int uniUiColorblindIntensity;
	static int uniBase;

	private int uniProjection;

	private int uniView;

	private int uniOutlineProjectionMatrix;

	private int uniMenuTex;
	private int uniMenuTexSourceDimensions;
	private int uniMenuTexTargetDimensions;
	private int uniMenuAlphaOverlay;

	private int uniMenuColorblindIntensity;

	private static Projection lastProjection;

	private int uniUiMap;
	private int uniUiProjection;

	private int uniUiView;

	private int uniMenuMap;
	private int uniMenuProjection;

	private int uniMenuProjection2;

	private int uniMenuView;

	private int uniMenuLoc;

	private int uniHandProjection;

	private int uniHandView;

	private int uniCursor;

	private int uniHandColor;

	private int uniOutlineProjection;

	private int uniOutlineView;

	private final com.vr.GLBuffer tmpOutlineVertexBuffer = new com.vr.GLBuffer("tmp outline vertex buffer");

	private VRRobot robot;

	private HandSelectState state = HandSelectState.IDLE;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI) {
		return !((renderable instanceof Player || renderable instanceof NPC) && drawingUI);
	}

	public void check(int result) throws IllegalStateException {
		if (XR_SUCCEEDED(result)) {
			return;
		}

		if(XR_ERROR_SESSION_NOT_RUNNING == result){
			return;
		}
		if (xrInstance != null) {
			ByteBuffer str = stackMalloc(XR_MAX_RESULT_STRING_SIZE);
			if (xrResultToString(xrInstance, result, str) >= 0) {
				throw new VRPlugin.XrResultException(memUTF8(str, memLengthNT1(str)));
			}
		}
		throw new VRPlugin.XrResultException("XR method returned " + result);
	}

	@SuppressWarnings("serial")
	public static class XrResultException extends RuntimeException {
		public XrResultException(String s) {
			super(s);
		}
	}

	public void createOpenXRInstance() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer pi = stack.mallocInt(1);

			check(xrEnumerateInstanceExtensionProperties((ByteBuffer)null, pi, null));
			int numExtensions = pi.get(0);

			XrExtensionProperties.Buffer properties = XRHelper.prepareExtensionProperties(stack, numExtensions);

			check(xrEnumerateInstanceExtensionProperties((ByteBuffer)null, pi, properties));

			System.out.printf("OpenXR loaded with %d extensions:%n", numExtensions);
			System.out.println("~~~~~~~~~~~~~~~~~~");

			boolean missingOpenGL = true;
			missingXrDebug = true;

			useEglGraphicsBinding = false;
			for (int i = 0; i < numExtensions; i++) {
				XrExtensionProperties prop = properties.get(i);

				String extensionName = prop.extensionNameString();
				System.out.println(extensionName);

				if (extensionName.equals(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME)) {
					missingOpenGL = false;
				}
				if (extensionName.equals(XR_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
					missingXrDebug = false;
				}
				if (extensionName.equals(XR_MNDX_EGL_ENABLE_EXTENSION_NAME)) {
					useEglGraphicsBinding = true;
				}
			}

			if (missingOpenGL) {
				throw new IllegalStateException("OpenXR library does not provide required extension: " + XR_KHR_OPENGL_ENABLE_EXTENSION_NAME);
			}

			if (useEglGraphicsBinding) {
				System.out.println("Going to use cross-platform experimental EGL for session creation");
			} else {
				System.out.println("Going to use platform-specific session creation");
			}

			PointerBuffer extensions = stack.mallocPointer(2);
			extensions.put(stack.UTF8(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME));
			if (useEglGraphicsBinding) {
				extensions.put(stack.UTF8(XR_MNDX_EGL_ENABLE_EXTENSION_NAME));
			} else if (!missingXrDebug) {
				// At the time of writing this, the OpenXR validation layers don't like EGL
				extensions.put(stack.UTF8(XR_EXT_DEBUG_UTILS_EXTENSION_NAME));
			}
			extensions.flip();
			System.out.println("~~~~~~~~~~~~~~~~~~");

			boolean useValidationLayer = false;

			check(xrEnumerateApiLayerProperties(pi, null));
			int numLayers = pi.get(0);

			XrApiLayerProperties.Buffer pLayers = XRHelper.prepareApiLayerProperties(stack, numLayers);
			check(xrEnumerateApiLayerProperties(pi, pLayers));
			System.out.println(numLayers + " XR layers are available:");
			for (int index = 0; index < numLayers; index++) {
				XrApiLayerProperties layer = pLayers.get(index);

				String layerName = layer.layerNameString();
				System.out.println(layerName);

				// At the time of wring this, the OpenXR validation layers don't like EGL
				if (!useEglGraphicsBinding && layerName.equals("XR_APILAYER_LUNARG_core_validation")) {
					useValidationLayer = true;
				}
			}
			System.out.println("-----------");

			PointerBuffer wantedLayers;
			if (useValidationLayer) {
				wantedLayers = stack.callocPointer(1);
				wantedLayers.put(0, stack.UTF8("XR_APILAYER_LUNARG_core_validation"));
				System.out.println("Enabling XR core validation");
			} else {
				System.out.println("Running without validation layers");
				wantedLayers = null;
			}

			XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.malloc(stack)
					.type$Default()
					.next(NULL)
					.createFlags(0)
					.applicationInfo(XrApplicationInfo.calloc(stack)
							.applicationName(stack.UTF8("HelloOpenXR"))
							.apiVersion(XR_CURRENT_API_VERSION))
					.enabledApiLayerNames(wantedLayers)
					.enabledExtensionNames(extensions);

			PointerBuffer pp = stack.mallocPointer(1);
			System.out.println("Creating OpenXR instance...");
			check(xrCreateInstance(createInfo, pp));
			xrInstance = new XrInstance(pp.get(0), createInfo);
			System.out.println("Created OpenXR instance");
		}
	}

	public void initializeOpenXRSystem() {
		try (MemoryStack stack = stackPush()) {
			//Get headset
			LongBuffer pl = stack.longs(0);

			check(xrGetSystem(
					xrInstance,
					XrSystemGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY),
					pl
			));

			systemID = pl.get(0);
			if (systemID == 0) {
				throw new IllegalStateException("No compatible headset detected");
			}
			System.out.printf("Headset found with System ID: %d\n", systemID);
		}
	}

	public void initializeAndBindOpenGL() {
		try (MemoryStack stack = stackPush()) {
			//Initialize OpenXR's OpenGL compatability
			XrGraphicsRequirementsOpenGLKHR graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.malloc(stack)
					.type$Default()
					.next(NULL)
					.minApiVersionSupported(0)
					.maxApiVersionSupported(0);

			xrGetOpenGLGraphicsRequirementsKHR(xrInstance, systemID, graphicsRequirements);

			int minMajorVersion = XR_VERSION_MAJOR(graphicsRequirements.minApiVersionSupported());
			int minMinorVersion = XR_VERSION_MINOR(graphicsRequirements.minApiVersionSupported());

			int maxMajorVersion = XR_VERSION_MAJOR(graphicsRequirements.maxApiVersionSupported());
			int maxMinorVersion = XR_VERSION_MINOR(graphicsRequirements.maxApiVersionSupported());

			System.out.println("The OpenXR runtime supports OpenGL " + minMajorVersion + "." + minMinorVersion
					+ " to OpenGL " + maxMajorVersion + "." + maxMinorVersion);

			// This example needs at least OpenGL 4.0
			if (maxMajorVersion < 4) {
				throw new UnsupportedOperationException("This example requires at least OpenGL 4.0");
			}
			int majorVersionToRequest = 4;
			int minorVersionToRequest = 0;

			// But when the OpenXR runtime requires a later version, we should respect that.
			// As a matter of fact, the runtime on my current laptop does, so this code is actually needed.
			if (minMajorVersion == 4) {
				minorVersionToRequest = 5;
			}

			//Init glfw
			if (!glfwInit()) {
				throw new IllegalStateException("Failed to initialize GLFW.");
			}

			glfwWindowHint(GLFW_VISIBLE, GL_FALSE);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, majorVersionToRequest);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minorVersionToRequest);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
			glfwWindowHint(GLFW_DOUBLEBUFFER, GL_FALSE);
			if (useEglGraphicsBinding) {
				glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
			}
			window = glfwCreateWindow(640, 480, "VR Runescape", NULL, NULL);
			glfwMakeContextCurrent(window);

			glCapabilities = GL.createCapabilities();

			log.info("Using device: {}", GL43C.glGetString(GL43C.GL_RENDERER));
			log.info("Using driver: {}", GL43C.glGetString(GL43C.GL_VERSION));

			if (!glCapabilities.OpenGL31)
			{
				throw new RuntimeException("OpenGL 3.1 is required but not available");
			}

			/*if (!glCapabilities.OpenGL43 && computeMode == VRPlugin.ComputeMode.OPENGL)
			{
				log.info("disabling compute shaders because OpenGL 4.3 is not available");
				computeMode = VRPlugin.ComputeMode.NONE;
			}

			if (computeMode == VRPlugin.ComputeMode.NONE)
			{
				sceneUploader.initSortingBuffers();
			}
			//sceneUploader.setStack(stack);*/

			// Check if OpenGL version is supported by OpenXR runtime
			int actualMajorVersion = glGetInteger(GL_MAJOR_VERSION);
			int actualMinorVersion = glGetInteger(GL_MINOR_VERSION);

			if (minMajorVersion > actualMajorVersion || (minMajorVersion == actualMajorVersion && minMinorVersion > actualMinorVersion)) {
				throw new IllegalStateException(
						"The OpenXR runtime supports only OpenGL " + minMajorVersion + "." + minMinorVersion +
								" and later, but we got OpenGL " + actualMajorVersion + "." + actualMinorVersion
				);
			}

			if (actualMajorVersion > maxMajorVersion || (actualMajorVersion == maxMajorVersion && actualMinorVersion > maxMinorVersion)) {
				throw new IllegalStateException(
						"The OpenXR runtime supports only OpenGL " + maxMajorVersion + "." + minMajorVersion +
								" and earlier, but we got OpenGL " + actualMajorVersion + "." + actualMinorVersion
				);
			}

			//Bind the OpenGL context to the OpenXR instance and create the session
			PointerBuffer pp = stack.mallocPointer(1);
			check(xrCreateSession(
					xrInstance,
					XRHelper.createGraphicsBindingOpenGL(
							XrSessionCreateInfo.malloc(stack)
									.type$Default()
									.next(NULL)
									.createFlags(0)
									.systemId(systemID),
							stack,
							window,
							useEglGraphicsBinding
					),
					pp
			));

			xrSession = new XrSession(pp.get(0), xrInstance);

			if (!missingXrDebug && !useEglGraphicsBinding) {
				XrDebugUtilsMessengerCreateInfoEXT ciDebugUtils = XrDebugUtilsMessengerCreateInfoEXT.calloc(stack)
						.type$Default()
						.messageSeverities(
								XR_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
										XR_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
										XR_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
						)
						.messageTypes(
								XR_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
										XR_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
										XR_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT |
										XR_DEBUG_UTILS_MESSAGE_TYPE_CONFORMANCE_BIT_EXT
						)
						.userCallback((messageSeverity, messageTypes, pCallbackData, userData) -> {
							XrDebugUtilsMessengerCallbackDataEXT callbackData = XrDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
							System.out.println("XR Debug Utils: " + callbackData.messageString());
							return 0;
						});

				System.out.println("Enabling OpenXR debug utils");
				check(xrCreateDebugUtilsMessengerEXT(xrInstance, ciDebugUtils, pp));
				xrDebugMessenger = new XrDebugUtilsMessengerEXT(pp.get(0), xrInstance);
			}
		}
	}

	public void createXRReferenceSpace() {
		try (MemoryStack stack = stackPush()) {
			PointerBuffer pp = stack.mallocPointer(1);

			check(xrCreateReferenceSpace(
					xrSession,
					XrReferenceSpaceCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL)
							.poseInReferenceSpace(XrPosef.malloc(stack)
									.orientation(XrQuaternionf.malloc(stack)
											.x(0)
											.y(0)
											.z(0)
											.w(1))
									.position$(XrVector3f.calloc(stack))),
					pp
			));

			xrAppSpace = new XrSpace(pp.get(0), xrSession);

			check(xrCreateReferenceSpace(
					xrSession,
					XrReferenceSpaceCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_VIEW)
							.poseInReferenceSpace(XrPosef.malloc(stack)
									.orientation(XrQuaternionf.malloc(stack)
											.x(0)
											.y(0)
											.z(0)
											.w(1))
									.position$(XrVector3f.calloc(stack))),
					pp
			));

			xrHeadSpace = new XrSpace(pp.get(0), xrSession);
		}
	}

	public void registerXRControllers(){
		try (MemoryStack stack = stackPush()) {
			PointerBuffer pp = stack.mallocPointer(1);

			check(xrCreateActionSet(
					xrInstance,
					XrActionSetCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionSetName(GpuByteBuffer.getBuffer("controller")),
					pp
			));

			xrActionSet = new XrActionSet(pp.get(0),xrInstance);

			LongBuffer buffer = stack.mallocLong(1);
			LongBuffer buffer2 = stack.mallocLong(1);
			check(xrStringToPath(xrInstance, "/user/hand/left", buffer));
			check(xrStringToPath(xrInstance, "/user/hand/right", buffer2));

			LongBuffer uniBuffer = stack.mallocLong(2)
					.put(0, buffer.get(0))
					.put(1,buffer2.get(0));

			leftHandPath = buffer.get(0);
			rightHandPath = buffer2.get(0);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("right_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("Right Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			rightClick = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("left_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("Left Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			leftClick = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("middle_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("Middle Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			middleClick = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("a_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("A Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			aButton = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("b_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("B Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			bButton = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("x_click"))
							.localizedActionName(GpuByteBuffer.getBuffer("X Click"))
							.actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			xButton = new XrAction(pp.get(0),xrActionSet);

			check(xrCreateAction(
					xrActionSet,
					XrActionCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionName(GpuByteBuffer.getBuffer("pose"))
							.localizedActionName(GpuByteBuffer.getBuffer("Pose"))
							.actionType(XR_ACTION_TYPE_POSE_INPUT)
							.countSubactionPaths(2)
							.subactionPaths(uniBuffer),
					pp
			));

			pose = new XrAction(pp.get(0),xrActionSet);

			LongBuffer buffer3 = stack.mallocLong(1);
			//check(xrStringToPath(xrInstance, "/interaction_profiles/khr/simple_controller", buffer3));
			check(xrStringToPath(xrInstance, "/interaction_profiles/oculus/touch_controller", buffer3));

			LongBuffer buffer4 = stack.mallocLong(1);
			LongBuffer buffer5 = stack.mallocLong(1);
			LongBuffer buffer6 = stack.mallocLong(1);
			LongBuffer buffer7 = stack.mallocLong(1);
			LongBuffer buffer8 = stack.mallocLong(1);
			LongBuffer buffer9 = stack.mallocLong(1);
			LongBuffer buffer10 = stack.mallocLong(1);
			LongBuffer buffer11 = stack.mallocLong(1);

			check(xrStringToPath(xrInstance, "/user/hand/left/input/aim/pose", buffer4));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/aim/pose", buffer5));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/trigger/value", buffer6));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/squeeze/value", buffer7));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/thumbstick/click", buffer8));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/a/click", buffer9));
			check(xrStringToPath(xrInstance, "/user/hand/right/input/b/click", buffer10));
			check(xrStringToPath(xrInstance, "/user/hand/left/input/x/click", buffer11));

			//TODO: THIS DOES IT
			XrActionSuggestedBinding.Buffer suggested = XrActionSuggestedBinding.malloc(8, stack)
					.put(0,XrActionSuggestedBinding.malloc(stack).action(pose).binding(buffer4.get(0)))
					.put(1,XrActionSuggestedBinding.malloc(stack).action(pose).binding(buffer5.get(0)))
					.put(2,XrActionSuggestedBinding.malloc(stack).action(leftClick).binding(buffer6.get(0)))
					.put(3,XrActionSuggestedBinding.malloc(stack).action(rightClick).binding(buffer7.get(0)))
					.put(4,XrActionSuggestedBinding.malloc(stack).action(middleClick).binding(buffer8.get(0)))
					.put(5,XrActionSuggestedBinding.malloc(stack).action(aButton).binding(buffer9.get(0)))
					.put(6,XrActionSuggestedBinding.malloc(stack).action(bButton).binding(buffer10.get(0)))
					.put(7,XrActionSuggestedBinding.malloc(stack).action(xButton).binding(buffer11.get(0)))
					;

			check(xrSuggestInteractionProfileBindings(
					xrInstance,
					XrInteractionProfileSuggestedBinding.malloc(stack)
							.type$Default()
							.next(NULL)
							.interactionProfile(buffer3.get(0))
							.suggestedBindings(suggested)
			));
		}
	}

	public void registerXRControllerActions(){
		try (MemoryStack stack = stackPush()) {
			PointerBuffer pp = stack.mallocPointer(1);

			check(xrCreateActionSpace(
					xrSession,
					XrActionSpaceCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(pose)
							.subactionPath(leftHandPath)
							.poseInActionSpace(XrPosef.malloc(stack)
									.orientation(XrQuaternionf.malloc(stack)
											.x(0)
											.y(0)
											.z(0)
											.w(1))
									.position$(XrVector3f.calloc(stack))),
					pp
			));

			leftHandSpace = new XrSpace(pp.get(0), xrSession);

			check(xrCreateActionSpace(
					xrSession,
					XrActionSpaceCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(pose)
							.subactionPath(rightHandPath)
							.poseInActionSpace(XrPosef.malloc(stack)
									.orientation(XrQuaternionf.malloc(stack)
											.x(0)
											.y(0)
											.z(0)
											.w(1))
									.position$(XrVector3f.calloc(stack))),
					pp
			));

			rightHandSpace = new XrSpace(pp.get(0), xrSession);

			check(xrAttachSessionActionSets(
					xrSession,
					XrSessionActionSetsAttachInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.actionSets(PointerBuffer.allocateDirect(1).put(xrActionSet.address()).flip())
			));
		}
	}

	public void createXRSwapchains() {
		try (MemoryStack stack = stackPush()) {
			XrSystemProperties systemProperties = XrSystemProperties.calloc(stack)
					.type$Default();
			check(xrGetSystemProperties(xrInstance, systemID, systemProperties));

			System.out.printf("Headset name:%s vendor:%d \n",
					memUTF8(memAddress(systemProperties.systemName())),
					systemProperties.vendorId());

			XrSystemTrackingProperties trackingProperties = systemProperties.trackingProperties();
			System.out.printf("Headset orientationTracking:%b positionTracking:%b \n",
					trackingProperties.orientationTracking(),
					trackingProperties.positionTracking());

			XrSystemGraphicsProperties graphicsProperties = systemProperties.graphicsProperties();
			System.out.printf("Headset MaxWidth:%d MaxHeight:%d MaxLayerCount:%d \n",
					graphicsProperties.maxSwapchainImageWidth(),
					graphicsProperties.maxSwapchainImageHeight(),
					graphicsProperties.maxLayerCount());

			IntBuffer pi = stack.mallocInt(1);

			check(xrEnumerateViewConfigurationViews(xrInstance, systemID, viewConfigType, pi, null));
			viewConfigs = XRHelper.fill(
					XrViewConfigurationView.calloc(pi.get(0)),
					XrViewConfigurationView.TYPE,
					XR_TYPE_VIEW_CONFIGURATION_VIEW
			);

			check(xrEnumerateViewConfigurationViews(xrInstance, systemID, viewConfigType, pi, viewConfigs));
			int viewCountNumber = pi.get(0);

			views = XRHelper.fill(
					XrView.calloc(viewCountNumber),
					XrView.TYPE,
					XR_TYPE_VIEW
			);

			if (viewCountNumber > 0) {
				check(xrEnumerateSwapchainFormats(xrSession, pi, null));
				LongBuffer swapchainFormats = stack.mallocLong(pi.get(0));
				check(xrEnumerateSwapchainFormats(xrSession, pi, swapchainFormats));

				long[] desiredSwapchainFormats = {
						GL_RGB10_A2,
						GL_RGBA16F,
						// The two below should only be used as a fallback, as they are linear color formats without enough bits for color
						// depth, thus leading to banding.
						GL_RGBA8,
						GL31.GL_RGBA8_SNORM
				};

				out:
				for (long glFormatIter : desiredSwapchainFormats) {
					for (int i = 0; i < swapchainFormats.limit(); i++) {
						if (glFormatIter == swapchainFormats.get(i)) {
							glColorFormat = glFormatIter;
							break out;
						}
					}
				}

				if (glColorFormat == 0) {
					throw new IllegalStateException("No compatable swapchain / framebuffer format availible");
				}

				swapchains = new VRPlugin.Swapchain[viewCountNumber];
				for (int i = 0; i < viewCountNumber; i++) {
					XrViewConfigurationView viewConfig = viewConfigs.get(i);

					VRPlugin.Swapchain swapchainWrapper = new VRPlugin.Swapchain();

					XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.createFlags(0)
							.usageFlags(XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT)
							.format(glColorFormat)
							.sampleCount(viewConfig.recommendedSwapchainSampleCount())
							.width(viewConfig.recommendedImageRectWidth())
							.height(viewConfig.recommendedImageRectHeight())
							.faceCount(1)
							.arraySize(1)
							.mipCount(1);

					PointerBuffer pp = stack.mallocPointer(1);
					check(xrCreateSwapchain(xrSession, swapchainCreateInfo, pp));

					swapchainWrapper.handle = new XrSwapchain(pp.get(0), xrSession);
					swapchainWrapper.width = swapchainCreateInfo.width();
					swapchainWrapper.height = swapchainCreateInfo.height();

					check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, null));
					int imageCount = pi.get(0);

					XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = XRHelper.fill(
							XrSwapchainImageOpenGLKHR.calloc(imageCount),
							XrSwapchainImageOpenGLKHR.TYPE,
							XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR
					);

					check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, XrSwapchainImageBaseHeader.create(swapchainImageBuffer)));
					swapchainWrapper.images = swapchainImageBuffer;
					swapchains[i] = swapchainWrapper;
				}
			}
		}
	}

	private void createOpenGLResourses() {
		swapchainFramebuffer = glGenFramebuffers();
		depthTextures = new HashMap<>(0);
		for (VRPlugin.Swapchain swapchain : swapchains) {
			for (XrSwapchainImageOpenGLKHR swapchainImage : swapchain.images) {
				int texture = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, texture);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32, swapchain.width, swapchain.height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer)null);
				depthTextures.put(swapchainImage, texture);
			}
		}
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	private int pollEvents() {
		glfwPollEvents();
		XrEventDataBaseHeader event = readNextOpenXREvent();
		if (event == null) {
			return NOTHING_POLLED;
		}

		do {
			switch (event.type()) {
				case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING: {
					XrEventDataInstanceLossPending instanceLossPending = XrEventDataInstanceLossPending.create(event);
					System.err.printf("XrEventDataInstanceLossPending by %d\n", instanceLossPending.lossTime());
					//*requestRestart = true;
					return POLLED;
				}
				case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED: {
					XrEventDataSessionStateChanged sessionStateChangedEvent = XrEventDataSessionStateChanged.create(event);
					return OpenXRHandleSessionStateChangedEvent(sessionStateChangedEvent/*, requestRestart*/);
				}
				case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
					break;
				case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
				default: {
					System.out.printf("Ignoring event type %d\n", event.type());
					break;
				}
			}
			event = readNextOpenXREvent();
		}
		while (event != null);

		return NOTHING_POLLED;
	}

	private XrEventDataBaseHeader readNextOpenXREvent() {
		// It is sufficient to just clear the XrEventDataBuffer header to
		// XR_TYPE_EVENT_DATA_BUFFER rather than recreate it every time
		eventDataBuffer.clear();
		eventDataBuffer.type$Default();
		int result = xrPollEvent(xrInstance, eventDataBuffer);
		if (result == XR_SUCCESS) {
			XrEventDataBaseHeader header = XrEventDataBaseHeader.create(eventDataBuffer.address());
			if (header.type() == XR_TYPE_EVENT_DATA_EVENTS_LOST) {
				XrEventDataEventsLost dataEventsLost = XrEventDataEventsLost.create(header);
				System.out.printf("%d events lost\n", dataEventsLost.lostEventCount());
			}
			return header;
		}
		if (result == XR_EVENT_UNAVAILABLE) {
			return null;
		}
		throw new IllegalStateException(String.format("[XrResult failure %d in xrPollEvent]", result));
	}

	int OpenXRHandleSessionStateChangedEvent(XrEventDataSessionStateChanged stateChangedEvent) {
		int oldState = sessionState;
		sessionState = stateChangedEvent.state();

		System.out.printf("XrEventDataSessionStateChanged: state %s->%s session=%d time=%d\n", oldState, sessionState, stateChangedEvent.session(), stateChangedEvent.time());

		if ((stateChangedEvent.session() != NULL) && (stateChangedEvent.session() != xrSession.address())) {
			System.err.println("XrEventDataSessionStateChanged for unknown session");
			return NOTHING_POLLED;
		}

		switch (sessionState) {
			case XR_SESSION_STATE_READY: {
				assert (xrSession != null);
				try (MemoryStack stack = stackPush()) {
					check(xrBeginSession(
							xrSession,
							XrSessionBeginInfo.malloc(stack)
									.type$Default()
									.next(NULL)
									.primaryViewConfigurationType(viewConfigType)
					));
					sessionRunning = true;
					return NOTHING_POLLED;
				}
			}
			case XR_SESSION_STATE_STOPPING: {
				assert (xrSession != null);
				sessionRunning = false;
				log.info("ENDING.");
				check(xrEndSession(xrSession));
				shutdown();
				return ENDING_POLLED;
			}
			case XR_SESSION_STATE_EXITING: {
				// Do not attempt to restart because user closed this session.
				//*requestRestart = false;
				return POLLED;
			}
			case XR_SESSION_STATE_LOSS_PENDING: {
				// Poll for a new instance.
				//*requestRestart = true;
				return POLLED;
			}
			default:
				return NOTHING_POLLED;
		}
	}

	HudHelper2 hudHelper;

	static int NOTHING_POLLED = 0;
	static int POLLED = 1;
	static int ENDING_POLLED = 2;

	private int targetOutlineBufferOffset;

	@Override
	protected void startUp()
	{
		root = new SceneContext(NUM_ZONES, NUM_ZONES);
		subs = new SceneContext[MAX_WORLDVIEWS];
		clientUploader = new com.vr.SceneUploader2(renderCallbackManager);
		mapUploader = new com.vr.SceneUploader2(renderCallbackManager);
		facePrioritySorter = new FacePrioritySorter(clientUploader);
		clientThread.invoke(() ->
		{
			try
			{
				hooks.registerRenderableDrawListener(drawListener);

				fboScene = -1;
				lastAnisotropicFilteringLevel = -1;
				targetOutlineBufferOffset = 0;

				//AWTContext.loadNatives();

				canvas = client.getCanvas();
				robot = new VRRobot(canvas);

				/*synchronized (canvas.getTreeLock())
				{
					if (!canvas.isValid())
					{
						return false;
					}

					awtContext = new AWTContext(canvas);
					awtContext.configurePixelFormat(0, 0, 0);
				}

				awtContext.createGLContext();*/

				canvas.setIgnoreRepaint(true);

				// lwjgl defaults to lwjgl- + user.name, but this breaks if the username would cause an invalid path
				// to be created.
				Configuration.SHARED_LIBRARY_EXTRACT_DIRECTORY.set("lwjgl-rl");

				/*glCapabilities = GL.createCapabilities();

				log.info("Using device: {}", glGetString(GL_RENDERER));
				log.info("Using driver: {}", glGetString(GL_VERSION));

				if (!glCapabilities.OpenGL33)
				{
					throw new RuntimeException("OpenGL 3.3 is required but not available");
				}*/

				createOpenXRInstance();
				registerXRControllers();
				initializeOpenXRSystem();
				initializeAndBindOpenGL();
				registerXRControllerActions();

				lwjglInitted = true;

				checkGLErrors();
				if (log.isDebugEnabled() && glCapabilities.glDebugMessageControl != 0)
				{
					debugCallback = GLUtil.setupDebugMessageCallback();
					if (debugCallback != null)
					{
						// [LWJGL] OpenGL debug message
						//	ID: 0x20071
						//	Source: API
						//	Type: OTHER
						//	Severity: NOTIFICATION
						//	Message: Buffer detailed info: Buffer object 2 (bound to GL_PIXEL_UNPACK_BUFFER_ARB, usage hint is GL_STREAM_DRAW) has been mapped WRITE_ONLY in SYSTEM HEAP memory (fast).
						glDebugMessageControl(GL_DEBUG_SOURCE_API, GL_DEBUG_TYPE_OTHER,
							GL_DONT_CARE, 0x20071, false);

						// [LWJGL] OpenGL debug message
						//	ID: 0x20052
						//	Source: API
						//	Type: PERFORMANCE
						//	Severity: MEDIUM
						//	Message: Pixel-path performance warning: Pixel transfer is synchronized with 3D rendering.
						glDebugMessageControl(GL_DEBUG_SOURCE_API, GL_DEBUG_TYPE_PERFORMANCE,
							GL_DONT_CARE, 0x20052, false);
					}
				}

				outlineVertexBuffer = new com.vr.GpuIntBuffer();

				setupSyncMode();

				initBuffers();
				initVao();
				initProgram();
				initInterfaceTexture();
				if (glCapabilities.OpenGL45)
				{
					glClipControl(GL_LOWER_LEFT, GL_ZERO_TO_ONE); // 1 near 0 far
				}

				client.setDrawCallbacks(this);
				client.setGpuFlags(DrawCallbacks.GPU
					| (config.removeVertexSnapping() ? DrawCallbacks.NO_VERTEX_SNAPPING : 0)
					| DrawCallbacks.ZBUF | DrawCallbacks.NORMALS
				);
				client.setExpandedMapLoading(config.expandedMapLoadingZones());

				// force rebuild of main buffer provider to enable alpha channel
				client.resizeCanvas();

				lastCanvasWidth = lastCanvasHeight = -1;
				lastStretchedCanvasWidth = lastStretchedCanvasHeight = -1;
				lastAntiAliasingMode = null;

				textureArrayId = -1;

				createXRReferenceSpace();
				createXRSwapchains();
				createOpenGLResourses();

				checkGLErrors();

				hudHelper = new HudHelper2();

				eventDataBuffer = XrEventDataBuffer.calloc()
						.type$Default();

				while (pollEvents() == NOTHING_POLLED && !glfwWindowShouldClose(window)) {
					if (sessionRunning) {
						break;
					} else {
						// Throttle loop since xrWaitFrame won't be called.
						Thread.sleep(250);
					}
				}

				if (client.getGameState() == GameState.LOGGED_IN)
				{
					startupWorldLoad();
				}

				checkGLErrors();
			}
			catch (Throwable e)
			{
				log.error("Error starting GPU plugin", e);

				SwingUtilities.invokeLater(() ->
				{
					try
					{
						pluginManager.setPluginEnabled(this, false);
						pluginManager.stopPlugin(this);
					}
					catch (PluginInstantiationException ex)
					{
						log.error("error stopping plugin", ex);
					}
				});

				shutdown();
			}
			return true;
		});
	}

	private void startupWorldLoad()
	{
		WorldView root = client.getTopLevelWorldView();
		Scene scene = root.getScene();
		loadScene(root, scene);
		swapScene(scene);

		for (WorldEntity subEntity : root.worldEntities())
		{
			WorldView sub = subEntity.getWorldView();
			log.debug("WorldView loading: {}", sub.getId());
			loadSubScene(sub, sub.getScene());
			swapSub(sub.getScene());
		}
	}

	protected void shutdown(){
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				pluginManager.setPluginEnabled(this, false);
				pluginManager.stopPlugin(this);
			}
			catch (PluginInstantiationException ex)
			{
				log.error("error stopping plugin", ex);
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() ->
		{

			glFinish();

			hooks.unregisterRenderableDrawListener(drawListener);
			// Destroy OpenXR
			eventDataBuffer.free();
			views.free();
			viewConfigs.free();
			for (VRPlugin.Swapchain swapchain : swapchains) {
				xrDestroySwapchain(swapchain.handle);
				swapchain.images.free();
			}

			xrDestroySpace(xrHeadSpace);
			xrDestroySpace(xrAppSpace);
			if (xrDebugMessenger != null) {
				xrDestroyDebugUtilsMessengerEXT(xrDebugMessenger);
			}
			xrDestroySession(xrSession);
			xrDestroyInstance(xrInstance);

			//Destroy OpenGL
			for (int texture : depthTextures.values()) {
				glDeleteTextures(texture);
			}
			glDeleteFramebuffers(swapchainFramebuffer);

			client.setGpuFlags(0);
			client.setDrawCallbacks(null);
			client.setUnlockedFps(false);
			client.setExpandedMapLoading(0);

			if (lwjglInitted)
			{
				if (textureArrayId != -1)
				{
					textureManager.freeTextureArray(textureArrayId);
					textureArrayId = -1;
				}

				root.free();

				shutdownInterfaceTexture();
				shutdownProgram();
				shutdownVao();
				shutdownBuffers();
				shutdownFbo();
			}

			/*if (awtContext != null)
			{
				awtContext.destroy();
				awtContext = null;
			}*/

			if (debugCallback != null)
			{
				debugCallback.free();
				debugCallback = null;
			}

			outlineVertexBuffer = null;

			glCapabilities = null;

			// force main buffer provider rebuild to turn off alpha channel
			client.resizeCanvas();
		});
	}

	@Provides
	VRPlugin2Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VRPlugin2Config.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().equals(VRPlugin2Config.GROUP))
		{
			if (configChanged.getKey().equals("unlockFps")
				|| configChanged.getKey().equals("vsyncMode")
				|| configChanged.getKey().equals("fpsTarget"))
			{
				log.debug("Rebuilding sync mode");
				clientThread.invokeLater(this::setupSyncMode);
			}
			else if (configChanged.getKey().equals("expandedMapLoadingChunks"))
			{
				clientThread.invokeLater(() ->
				{
					client.setExpandedMapLoading(config.expandedMapLoadingZones());
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						client.setGameState(GameState.LOADING);
					}
				});
			}
			else if (configChanged.getKey().equals("removeVertexSnapping"))
			{
				log.debug("Toggle {}", configChanged.getKey());
				client.setGpuFlags(DrawCallbacks.GPU
					| (config.removeVertexSnapping() ? DrawCallbacks.NO_VERTEX_SNAPPING : 0)
					| DrawCallbacks.ZBUF
				);
			}
			else if (configChanged.getKey().equals("uiScalingMode") || configChanged.getKey().equals("colorBlindMode"))
			{
				clientThread.invokeLater(() ->
				{
					log.debug("Recompiling shaders");
					shutdownProgram();
					try {
						initProgram();
					} catch (ShaderException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
	}

	private void setupSyncMode()
	{
		final boolean unlockFps = config.unlockFps();
		client.setUnlockedFps(unlockFps);

		// Without unlocked fps, the client manages sync on its 20ms timer
		VRPlugin2Config.SyncMode syncMode = unlockFps
			? this.config.syncMode()
			: VRPlugin2Config.SyncMode.OFF;

		int swapInterval = 0;
		switch (syncMode)
		{
			case ON:
				swapInterval = 1;
				break;
			case OFF:
				swapInterval = 0;
				break;
			case ADAPTIVE:
				swapInterval = -1;
				break;
		}

		/*int actualSwapInterval = awtContext.setSwapInterval(swapInterval);
		if (actualSwapInterval != swapInterval)
		{
			log.info("unsupported swap interval {}, got {}", swapInterval, actualSwapInterval);
		}

		client.setUnlockedFpsTarget(actualSwapInterval == 0 ? config.fpsTarget() : 0);*/
		checkGLErrors();
	}

	private Template createTemplate()
	{
		Template template = new Template();
		template.add(key ->
		{
			switch (key)
			{
				case "texture_config":
					return "#define TEXTURE_COUNT " + com.vr.TextureManager.TEXTURE_COUNT + "\n";
				case "sampling_mode":
					return "#define SAMPLING_MODE " + config.uiScalingMode().ordinal() + "\n";
				case "colorblind_mode":
					return "#define COLORBLIND_MODE " + config.colorBlindMode().ordinal() + "\n";
			}
			return null;
		});
		template.addInclude(VRPlugin2.class);
		return template;
	}

	private void initProgram() throws com.vr.ShaderException
	{
		// macOS core profile has no default VAO, so the shaders won't validate unless a VAO is bound
		glBindVertexArray(vaoUiHandle);

		Template template = createTemplate();
		glProgram = PROGRAM.compile(template);
		glUiProgram = UI_PROGRAM.compile(template);
		glOutlineProgram = OUTLINE_PROGRAM.compile(template);
		glHandProgram = HAND_PROGRAM.compile(template);
		glMenuProgram = MENU_PROGRAM.compile(template);
		hudHelper.glHintProgram = HINT_PROGRAM.compile(template);
		hudHelper.glHud3Program = HUD3_PROGRAM.compile(template);
		hudHelper.glHudProgram = HUD_PROGRAM.compile(template);
		hudHelper.glHud2Program = HUD2_PROGRAM.compile(template);

		glBindVertexArray(0);

		initUniforms();
	}

	private void initUniforms()
	{
		uniProjection = GL43C.glGetUniformLocation(glProgram, "projection");
		uniView = GL43C.glGetUniformLocation(glProgram, "viewMatrix");

		uniWorldProj = glGetUniformLocation(glProgram, "worldProj");
		uniEntityProj = glGetUniformLocation(glProgram, "entityProj");
		uniEntityTint = glGetUniformLocation(glProgram, "entityTint");
		uniSmoothBanding = glGetUniformLocation(glProgram, "smoothBanding");
		uniBrightness = glGetUniformLocation(glProgram, "brightness");
		uniUseFog = glGetUniformLocation(glProgram, "useFog");
		uniFogColor = glGetUniformLocation(glProgram, "fogColor");
		uniFogDepth = glGetUniformLocation(glProgram, "fogDepth");
		uniDrawDistance = glGetUniformLocation(glProgram, "drawDistance");
		uniExpandedMapLoadingChunks = glGetUniformLocation(glProgram, "expandedMapLoadingChunks");
		uniTextureLightMode = glGetUniformLocation(glProgram, "textureLightMode");
		uniTick = glGetUniformLocation(glProgram, "tick");
		uniBlockMain = glGetUniformBlockIndex(glProgram, "uniforms");
		uniTextures = glGetUniformLocation(glProgram, "textures");
		uniTextureAnimations = glGetUniformLocation(glProgram, "textureAnimations");
		uniBase = glGetUniformLocation(glProgram, "base");
		uniColorblindIntensity = glGetUniformLocation(glProgram, "colorblindIntensity");

		uniTex = glGetUniformLocation(glUiProgram, "tex");
		uniTexTargetDimensions = glGetUniformLocation(glUiProgram, "targetDimensions");
		uniTexSourceDimensions = glGetUniformLocation(glUiProgram, "sourceDimensions");
		uniUiAlphaOverlay = glGetUniformLocation(glUiProgram, "alphaOverlay");
		uniUiColorblindIntensity = glGetUniformLocation(glUiProgram, "colorblindIntensity");

		uniOutlineProjection = GL43C.glGetUniformLocation(glOutlineProgram, "projection");
		uniOutlineView = GL43C.glGetUniformLocation(glOutlineProgram, "viewMatrix");
		uniOutlineEntityProj = glGetUniformLocation(glOutlineProgram, "entityProj");
		uniOutlineProjectionMatrix = GL43C.glGetUniformLocation(glOutlineProgram, "projectionMatrix");

		uniUiMap = GL43C.glGetUniformLocation(glUiProgram, "map");
		uniUiProjection = GL43C.glGetUniformLocation(glUiProgram, "projection");
		uniUiView = GL43C.glGetUniformLocation(glUiProgram, "viewMatrix");

		uniHandProjection = GL43C.glGetUniformLocation(glHandProgram, "projection");
		uniHandView = GL43C.glGetUniformLocation(glHandProgram, "viewMatrix");
		uniCursor = GL43C.glGetUniformLocation(glHandProgram, "cursor");
		uniHandColor = GL43C.glGetUniformLocation(glHandProgram, "color");

		uniMenuTex = GL43C.glGetUniformLocation(glMenuProgram, "tex");
		uniMenuTexTargetDimensions = GL43C.glGetUniformLocation(glMenuProgram, "targetDimensions");
		uniMenuTexSourceDimensions = GL43C.glGetUniformLocation(glMenuProgram, "sourceDimensions");
		uniMenuAlphaOverlay = GL43C.glGetUniformLocation(glMenuProgram, "alphaOverlay");
		uniMenuColorblindIntensity = glGetUniformLocation(glMenuProgram, "colorblindIntensity");

		uniMenuMap = GL43C.glGetUniformLocation(glMenuProgram, "map");
		uniMenuProjection = GL43C.glGetUniformLocation(glMenuProgram, "projection");
		uniMenuProjection2 = GL43C.glGetUniformLocation(glMenuProgram, "projection2");
		uniMenuView = GL43C.glGetUniformLocation(glMenuProgram, "viewMatrix");
		uniMenuLoc = GL43C.glGetUniformLocation(glMenuProgram, "loc");
	}

	private void shutdownProgram()
	{
		glDeleteProgram(glProgram);
		glProgram = 0;

		glDeleteProgram(glUiProgram);
		glUiProgram = 0;
	}

	private void initVao()
	{
		vaoOutlineTemp = GL43C.glGenVertexArrays();
		GL43C.glBindVertexArray(vaoOutlineTemp);

		GL43C.glEnableVertexAttribArray(0);
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, tmpOutlineVertexBuffer.glBufferId);
		GL43C.glVertexAttribIPointer(0, 4, GL43C.GL_INT, 0, 0);

		vaoMenuHandle = GL43C.glGenVertexArrays();
		vboMenuHandle = GL43C.glGenBuffers();
		glBindVertexArray(vaoMenuHandle);
		glBindBuffer(GL_ARRAY_BUFFER, vboMenuHandle);
		GL43C.glBufferData(GL_ARRAY_BUFFER, GL43C.GL_FLOAT * 6 * 5, GL_DYNAMIC_DRAW);
		GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 0);
		GL43C.glEnableVertexAttribArray(0);

		// texture coord attribute
		GL43C.glVertexAttribPointer(1, 2, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
		GL43C.glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);

		// Create UI VAO
		vaoUiHandle = GL43C.glGenVertexArrays();
		// Create UI buffer
		vboUiHandle = GL43C.glGenBuffers();
		GL43C.glBindVertexArray(vaoUiHandle);

		FloatBuffer vboUiBuf = com.vr.GpuFloatBuffer.allocateDirect(5 * 4);
		//TODO:figure out placement for this
		vboUiBuf.put(new float[]{
				// positions     // texture coords
				0.2f, 0.2f, -0.02f, 1.0f, 0f, // top right
				0.2f, -0.2f, -0.02f, 1.0f, 1f, // bottom right
				-0.2f, -0.2f, -0.02f, 0.0f, 1f, // bottom left
				-0.2f, 0.2f, -0.02f, 0.0f, 0f  // top left
				//0.3f, 0.0f, -0.021f, 1.0f, 0f, // top right
				//0.3f, -0.3f, -0.021f, 1.0f, 1f, // bottom right
				//-0.0f, -0.3f, -0.021f, 0.0f, 1f, // bottom left
				//-0.0f, 0.0f, -0.021f, 0.0f, 0f  // top left
		});
		vboUiBuf.rewind();
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, vboUiHandle);
		GL43C.glBufferData(GL43C.GL_ARRAY_BUFFER, vboUiBuf, GL43C.GL_STATIC_DRAW);

		// position attribute
		GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 0);
		GL43C.glEnableVertexAttribArray(0);

		// texture coord attribute
		GL43C.glVertexAttribPointer(1, 2, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
		GL43C.glEnableVertexAttribArray(1);

		// Create Hand VAO
		vaoHandHandle = GL43C.glGenVertexArrays();
		// Create Hand buffer
		vboHandHandle = GL43C.glGenBuffers();
		GL43C.glBindVertexArray(vaoHandHandle);

		FloatBuffer vboHandBuf = com.vr.GpuFloatBuffer.allocateDirect(3 * 15);
		//TODO:figure out placement for this
		vboHandBuf.put(new float[]{
				// positions     // texture coords
				0.0f, 0.002f, 10.0f, // top right
				0.0f, -0.002f, -10.0f,  // bottom right
				0.0f, -0.002f, 10.0f,  // bottom left
				0.0f, 0.002f, 10.0f,  // top left
				0.0f, 0.002f, -10.0f,  // top left
				0.0f, -0.002f, -10.0f,  // top left
				0.002f, 0.0f, 10.0f,  // top right
				-0.002f, 0.0f, -10.0f,  // bottom right
				-0.002f, 0.0f, 10.0f,  // bottom left
				0.002f, 0.0f, 10.0f,   // top left
				0.002f, 0.0f, -10.0f,  // top left
				-0.002f, 0.0f, -10.0f,  // top left
				-0.01f, 0.0f, 0.0f,
				0.01f, 0.0f, 0.0f,
				0.0f, 0.0f, -0.02f
		});
		vboHandBuf.rewind();
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, vboHandHandle);
		GL43C.glBufferData(GL43C.GL_ARRAY_BUFFER, vboHandBuf, GL43C.GL_STATIC_DRAW);

		// position attribute
		GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 3 * Float.BYTES, 0);
		GL43C.glEnableVertexAttribArray(0);

		// unbind VBO
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, 0);
	}

	private void shutdownVao()
	{
		glDeleteBuffers(vboUiHandle);
		vboUiHandle = 0;

		glDeleteVertexArrays(vaoUiHandle);
		vaoUiHandle = 0;

		GL43C.glDeleteVertexArrays(vaoOutlineTemp);
		vaoOutlineTemp = 0;

		GL43C.glDeleteVertexArrays(vaoHandHandle);
		vaoHandHandle = 0;
	}

	private void initBuffers()
	{
		uniformBuffer = new com.vr.GpuFloatBuffer2(UNIFORM_BUFFER_SIZE);
		initGlBuffer(glUniformBuffer);
		Zone.initBuffer();

		initGlBuffer(tmpOutlineVertexBuffer);


		vaoO = new VAOList();
		vaoA = new VAOList();
		vaoPO = new VAOList();
	}

	private void initGlBuffer(com.vr.GLBuffer glBuffer)
	{
		glBuffer.glBufferId = glGenBuffers();
	}

	private void shutdownBuffers()
	{
		destroyGlBuffer(glUniformBuffer);
		uniformBuffer = null;
		Zone.freeBuffer();

		destroyGlBuffer(tmpOutlineVertexBuffer);

		if (vaoO != null)
		{
			vaoO.free();
		}
		if (vaoA != null)
		{
			vaoA.free();
		}
		if (vaoPO != null)
		{
			vaoPO.free();
		}
		vaoO = vaoA = vaoPO = null;
	}

	private void destroyGlBuffer(com.vr.GLBuffer glBuffer)
	{
		if (glBuffer.glBufferId != -1)
		{
			glDeleteBuffers(glBuffer.glBufferId);
			glBuffer.glBufferId = -1;
		}
		glBuffer.size = -1;
	}

	private void initInterfaceTexture()
	{
		interfacePbo = glGenBuffers();

		interfaceTexture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);

		menuPbo = glGenBuffers();

		menuTexture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, menuTexture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	private void shutdownInterfaceTexture()
	{
		glDeleteBuffers(interfacePbo);
		glDeleteTextures(interfaceTexture);
		interfaceTexture = -1;

		glDeleteBuffers(menuPbo);
		glDeleteTextures(menuTexture);
		menuTexture = -1;
	}

	private void initFbo(int width, int height, int aaSamples)
	{
		final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
		final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

		width = getScaledValue(transform.getScaleX(), width);
		height = getScaledValue(transform.getScaleY(), height);

		if (aaSamples > 0)
		{
			glEnable(GL_MULTISAMPLE);
		}
		else
		{
			glDisable(GL_MULTISAMPLE);
		}

		// Create and bind the FBO
		fboScene = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fboScene);

		// Color render buffer
		rboColorBuffer = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, rboColorBuffer);
		glRenderbufferStorageMultisample(GL_RENDERBUFFER, aaSamples, GL_RGBA, width, height);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rboColorBuffer);

		// Depth render buffer
		rboDepthBuffer = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, rboDepthBuffer);
		glRenderbufferStorageMultisample(GL_RENDERBUFFER, aaSamples, GL_DEPTH_COMPONENT32F, width, height);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rboDepthBuffer);

		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE)
		{
			throw new RuntimeException("FBO is incomplete. status: " + status);
		}

		// Reset
		glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
		glBindRenderbuffer(GL_RENDERBUFFER, 0);
	}

	boolean foundHoverRender = false;
	boolean foundInteractRender = false;

	///TODO:  GameObjects and players are spinning too much.  Probably orientation is messed up.
	private void renderMouseover(Projection projection, int orientation, int x, int y, int z,Renderable renderable)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0)
		{
			return;
		}

		MenuEntry entry = client.isMenuOpen() ? hoveredMenuEntry(menuEntries) : menuEntries[menuEntries.length - 1];
		MenuAction menuAction = entry.getType();

		switch (menuAction)
		{
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case EXAMINE_OBJECT:
			{
				int x3 = entry.getParam0();
				int y3 = entry.getParam1();
				int id = entry.getIdentifier();
				TileObject tileObject = findTileObject(x3, y3, id);

				if (tileObject != null && (tileObject != getInteractedObject()))
				{
					//System.out.println("FOUND");
					int x2 = tileObject.getX();
					int y2 = tileObject.getY();
					int z2 = tileObject.getZ();
					//System.out.println("1");
					if(tileObject instanceof WallObject){
						//System.out.println("1");
						if(((WallObject) tileObject).getRenderable1() != null) {
							Renderable rend = (((WallObject) tileObject).getRenderable1() instanceof Model)?((WallObject) tileObject).getRenderable1():((WallObject) tileObject).getRenderable1().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
						}
						if(((WallObject) tileObject).getRenderable2() != null) {
							Renderable rend = (((WallObject) tileObject).getRenderable2() instanceof Model)?((WallObject) tileObject).getRenderable2():((WallObject) tileObject).getRenderable2().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
						}
						if(((WallObject) tileObject).getRenderable1() != null) {
							Renderable rend = (((WallObject) tileObject).getRenderable1() instanceof Model)?((WallObject) tileObject).getRenderable1():((WallObject) tileObject).getRenderable1().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
						if(((WallObject) tileObject).getRenderable2() != null) {
							Renderable rend = (((WallObject) tileObject).getRenderable2() instanceof Model)?((WallObject) tileObject).getRenderable2():((WallObject) tileObject).getRenderable2().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
					} else if (tileObject instanceof GroundObject){
						if(((GroundObject) tileObject).getRenderable() != null) {
							Renderable rend = (((GroundObject) tileObject).getRenderable() instanceof Model)?((GroundObject) tileObject).getRenderable():((GroundObject) tileObject).getRenderable().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
						//System.out.println("2");
					} else if (tileObject instanceof DecorativeObject){
						if(((DecorativeObject) tileObject).getRenderable() != null) {
							Renderable rend = (((DecorativeObject) tileObject).getRenderable() instanceof Model)?((DecorativeObject) tileObject).getRenderable():((DecorativeObject) tileObject).getRenderable().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
						}
						if(((DecorativeObject) tileObject).getRenderable2() != null) {
							Renderable rend = (((DecorativeObject) tileObject).getRenderable2() instanceof Model)?((DecorativeObject) tileObject).getRenderable2():((DecorativeObject) tileObject).getRenderable2().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
						}
						if(((DecorativeObject) tileObject).getRenderable() != null) {
							Renderable rend = (((DecorativeObject) tileObject).getRenderable() instanceof Model)?((DecorativeObject) tileObject).getRenderable():((DecorativeObject) tileObject).getRenderable().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
						if(((DecorativeObject) tileObject).getRenderable2() != null) {
							Renderable rend = (((DecorativeObject) tileObject).getRenderable2() instanceof Model)?((DecorativeObject) tileObject).getRenderable2():((DecorativeObject) tileObject).getRenderable2().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
						//System.out.println("3");
					} else if (tileObject instanceof GameObject){
						if(((GameObject) tileObject).getRenderable() != null) {
							Renderable rend = (((GameObject) tileObject).getRenderable() instanceof Model) ? ((GameObject) tileObject).getRenderable() : ((GameObject) tileObject).getRenderable().getModel();
							foundHoverRender = true;
							targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection, (Model) rend, ((GameObject) tileObject).getModelOrientation(), x2, z2, y2, outlineVertexBuffer, OBJECT_HOVER_HIGHLIGHT_COLOR);
						}
					}
					//modelOutlineRenderer.drawOutline(tileObject, config.borderWidth(), OBJECT_HOVER_HIGHLIGHT_COLOR, config.outlineFeather());
				}
				break;
			}
			case WIDGET_TARGET_ON_NPC:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case EXAMINE_NPC:
			{
				NPC npc = entry.getNpc();
				if (npc != null && (npc != getInteractedTarget()) && npc == renderable)
				{
					int highlightColor = menuAction == MenuAction.NPC_SECOND_OPTION
							|| menuAction == MenuAction.WIDGET_TARGET_ON_NPC && WidgetUtil.componentToInterface(client.getSelectedWidget().getId()) == InterfaceID.SPELLBOOK
							? NPC_ATTACK_HOVER_HIGHLIGHT_COLOR : NPC_HOVER_HIGHLIGHT_COLOR;
					targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection,((NPC)npc).getModel(),orientation, x, y, z,outlineVertexBuffer,highlightColor);
					foundHoverRender = true;
				}
				//System.out.println("5");
				break;
			}
		}
		if(targetOutlineBufferOffset > 0) {
			//System.out.println("HERE: " + targetOutlineBufferOffset);
		}
	}

	private static final int INTERACT_CLICK_COLOR = 0xFFFFFF;
	private static final int OBJECT_HOVER_HIGHLIGHT_COLOR = 0x00FFFF;
	private static final int NPC_ATTACK_HOVER_HIGHLIGHT_COLOR = 0xFFFF00;
	private static final int NPC_HOVER_HIGHLIGHT_COLOR = 0xFFFF00;
	private static final int NPC_ATTACK_HIGHLIGHT_COLOR = 0xFF0000;
	private static final int OBJECT_INTERACT_HIGHLIGHT_COLOR = 0xFF0000;
	private static final int NPC_INTERACT_HIGHLIGHT_COLOR = 0xFF0000;

	private void renderTarget(Projection projection, int orientation, int x, int y, int z,Renderable renderable)
	{
		TileObject interactedObject = getInteractedObject();
		if (interactedObject != null)
		{
			int x2 = interactedObject.getX();
			int y2 = interactedObject.getY();
			int z2 = interactedObject.getZ();

			int clickColor = getClickColor(OBJECT_HOVER_HIGHLIGHT_COLOR, OBJECT_INTERACT_HIGHLIGHT_COLOR,
					client.getGameCycle() - getGameCycle());
			//modelOutlineRenderer.drawOutline(interactedObject, config.borderWidth(), clickColor, config.outlineFeather());
			if(interactedObject instanceof WallObject){
				//System.out.println("6");
				if(((WallObject) interactedObject).getRenderable1() != null) {
					Renderable rend = (((WallObject) interactedObject).getRenderable1() instanceof Model)?((WallObject) interactedObject).getRenderable1():((WallObject) interactedObject).getRenderable1().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
				}
				if(((WallObject) interactedObject).getRenderable2() != null) {
					Renderable rend = (((WallObject) interactedObject).getRenderable2() instanceof Model)?((WallObject) interactedObject).getRenderable2():((WallObject) interactedObject).getRenderable2().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
				}
				if(((WallObject) interactedObject).getRenderable1() != null) {
					Renderable rend = (((WallObject) interactedObject).getRenderable1() instanceof Model)?((WallObject) interactedObject).getRenderable1():((WallObject) interactedObject).getRenderable1().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, clickColor);
				}
				if(((WallObject) interactedObject).getRenderable2() != null) {
					Renderable rend = (((WallObject) interactedObject).getRenderable2() instanceof Model)?((WallObject) interactedObject).getRenderable2():((WallObject) interactedObject).getRenderable2().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, clickColor);
				}
			} else if (interactedObject instanceof GroundObject){
				//System.out.println("7");
				if(((GroundObject) interactedObject).getRenderable() != null) {
					Renderable rend = (((GroundObject) interactedObject).getRenderable() instanceof Model) ? ((GroundObject) interactedObject).getRenderable() : ((GroundObject) interactedObject).getRenderable().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, clickColor);
				}
			} else if (interactedObject instanceof DecorativeObject){
				//System.out.println("8");
				if(((DecorativeObject) interactedObject).getRenderable() != null) {
					Renderable rend = (((DecorativeObject) interactedObject).getRenderable() instanceof Model)?((DecorativeObject) interactedObject).getRenderable():((DecorativeObject) interactedObject).getRenderable().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
				}
				if(((DecorativeObject) interactedObject).getRenderable2() != null) {
					Renderable rend = (((DecorativeObject) interactedObject).getRenderable2() instanceof Model)?((DecorativeObject) interactedObject).getRenderable2():((DecorativeObject) interactedObject).getRenderable2().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase1(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer);
				}
				if(((DecorativeObject) interactedObject).getRenderable() != null) {
					Renderable rend = (((DecorativeObject) interactedObject).getRenderable() instanceof Model)?((DecorativeObject) interactedObject).getRenderable():((DecorativeObject) interactedObject).getRenderable().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, clickColor);
				}
				if(((DecorativeObject) interactedObject).getRenderable2() != null) {
					Renderable rend = (((DecorativeObject) interactedObject).getRenderable2() instanceof Model)?((DecorativeObject) interactedObject).getRenderable2():((DecorativeObject) interactedObject).getRenderable2().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlinePhase2(projection,(Model)rend,0, x2, z2, y2, outlineVertexBuffer, clickColor);
				}
			} else if (interactedObject instanceof GameObject){
				if(((GameObject) interactedObject).getRenderable() != null) {
					Renderable rend = (((GameObject) interactedObject).getRenderable() instanceof Model) ? ((GameObject) interactedObject).getRenderable() : ((GameObject) interactedObject).getRenderable().getModel();
					foundInteractRender = true;
					targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection, (Model) rend, ((GameObject) interactedObject).getModelOrientation(), x2, z2, y2, outlineVertexBuffer, clickColor);
				}
			}
		}

		Actor target = getInteractedTarget();
		if (target instanceof NPC && target == renderable)
		{
			int startColor = isAttacked() ? NPC_ATTACK_HOVER_HIGHLIGHT_COLOR : NPC_HOVER_HIGHLIGHT_COLOR;
			int endColor = isAttacked() ? NPC_ATTACK_HIGHLIGHT_COLOR : NPC_INTERACT_HIGHLIGHT_COLOR;
			int clickColor = getClickColor(startColor, endColor,
					client.getGameCycle() - getGameCycle());
			//modelOutlineRenderer.drawOutline((NPC) target, config.borderWidth(), clickColor, config.outlineFeather());
			targetOutlineBufferOffset += clientUploader.pushModelOutlineCombined(projection,((NPC)target).getModel(),orientation, x, y, z,outlineVertexBuffer,clickColor);
			//System.out.println("10");
			foundInteractRender = true;
		}
		if(targetOutlineBufferOffset > 0) {
			//System.out.println("HERE: " + targetOutlineBufferOffset);
		}
	}

	private int getClickColor(int start, int end, long time)
	{
		if (time < 5)
		{
			return (int)(start+((INTERACT_CLICK_COLOR-start)*time / 5f));
		}
		else if (time < 10)
		{
			return (int)(INTERACT_CLICK_COLOR+((end-INTERACT_CLICK_COLOR)*(time - 5) / 5f));
		}
		return end;
	}

	private MenuEntry hoveredMenuEntry(final MenuEntry[] menuEntries)
	{
		final int menuX = client.getMenuX();
		final int menuY = client.getMenuY();
		final int menuWidth = client.getMenuWidth();
		final Point mousePosition = client.getMouseCanvasPosition();

		int dy = mousePosition.getY() - menuY;
		dy -= 19; // Height of Choose Option
		if (dy < 0)
		{
			return menuEntries[menuEntries.length - 1];
		}

		int idx = dy / 15; // Height of each menu option
		idx = menuEntries.length - 1 - idx;

		if (mousePosition.getX() > menuX && mousePosition.getX() < menuX + menuWidth
				&& idx >= 0 && idx < menuEntries.length)
		{
			return menuEntries[idx];
		}
		return menuEntries[menuEntries.length - 1];
	}

	@Getter(AccessLevel.PACKAGE)
	private TileObject interactedObject;
	private NPC interactedNpc;
	@Getter(AccessLevel.PACKAGE)
	boolean attacked;
	private int clickTick;
	@Getter(AccessLevel.PACKAGE)
	private int gameCycle;

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		if (npcDespawned.getNpc() == interactedNpc)
		{
			interactedNpc = null;
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		if (interactingChanged.getSource() == client.getLocalPlayer()
				&& client.getTickCount() > clickTick && interactingChanged.getTarget() != interactedNpc)
		{
			interactedNpc = null;
			attacked = interactingChanged.getTarget() != null && interactingChanged.getTarget().getCombatLevel() > 0;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		switch (menuOptionClicked.getMenuAction())
		{
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			{
				int x = menuOptionClicked.getParam0();
				int y = menuOptionClicked.getParam1();
				int id = menuOptionClicked.getId();
				interactedObject = findTileObject(x, y, id);
				interactedNpc = null;
				clickTick = client.getTickCount();
				gameCycle = client.getGameCycle();
				break;
			}
			case WIDGET_TARGET_ON_NPC:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			{
				interactedObject = null;
				interactedNpc = menuOptionClicked.getMenuEntry().getNpc();
				attacked = menuOptionClicked.getMenuAction() == MenuAction.NPC_SECOND_OPTION ||
						menuOptionClicked.getMenuAction() == MenuAction.WIDGET_TARGET_ON_NPC
								&& client.getSelectedWidget() != null
								&& WidgetUtil.componentToInterface(client.getSelectedWidget().getId()) == InterfaceID.SPELLBOOK;
				clickTick = client.getTickCount();
				gameCycle = client.getGameCycle();
				break;
			}
			// Any menu click which clears an interaction
			case WALK:
			case WIDGET_TARGET_ON_WIDGET:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_PLAYER:
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				interactedObject = null;
				interactedNpc = null;
				break;
			default:
				if (menuOptionClicked.isItemOp())
				{
					interactedObject = null;
					interactedNpc = null;
				}
		}
	}

	TileObject findTileObject(int x, int y, int id)
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		Tile tile = null;
		if(client.getPlane() < tiles.length){
			Tile[][] tiles2 = tiles[client.getPlane()];
			if(x < tiles2.length){
				Tile[] tiles3 = tiles2[x];
				if(y < tiles3.length) {
					tile = tiles3[y];
				}
			}
		}
		//Tile tile = tiles[client.getPlane()][x][y];
		if (tile != null)
		{
			for (GameObject gameObject : tile.getGameObjects())
			{
				if (gameObject != null && gameObject.getId() == id)
				{
					return gameObject;
				}
			}

			WallObject wallObject = tile.getWallObject();
			if (wallObject != null && wallObject.getId() == id)
			{
				return wallObject;
			}

			DecorativeObject decorativeObject = tile.getDecorativeObject();
			if (decorativeObject != null && decorativeObject.getId() == id)
			{
				return decorativeObject;
			}

			GroundObject groundObject = tile.getGroundObject();
			if (groundObject != null && groundObject.getId() == id)
			{
				return groundObject;
			}
		}
		return null;
	}

	@Nullable
	Actor getInteractedTarget()
	{
		if(client.getLocalPlayer() == null) return null;
		return interactedNpc != null ? interactedNpc : client.getLocalPlayer().getInteracting();
	}

	private void shutdownFbo()
	{
		if (fboScene != -1)
		{
			glDeleteFramebuffers(fboScene);
			fboScene = -1;
		}

		if (rboColorBuffer != 0)
		{
			glDeleteRenderbuffers(rboColorBuffer);
			rboColorBuffer = 0;
		}

		if (rboDepthBuffer != 0)
		{
			glDeleteRenderbuffers(rboDepthBuffer);
			rboDepthBuffer = 0;
		}
	}

	static void updateEntityProjection(Projection projection)
	{
		if (lastProjection != projection)
		{
			float[] p = projection instanceof FloatProjection ? ((FloatProjection) projection).getProjection() : com.vr.Mat4.identity();
			glUniformMatrix4fv(uniEntityProj, false, p);
			glUniformMatrix4fv(uniOutlineEntityProj, false, p);
			lastProjection = projection;
		}
	}

	@Override
	public void preSceneDraw(Scene scene,
		float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw,
		int minLevel, int level, int maxLevel, Set<Integer> hideRoofIds)
	{
		SceneContext ctx = context(scene);
		if (ctx != null)
		{
			ctx.cameraX = (int) cameraX;
			ctx.cameraY = (int) cameraY;
			ctx.cameraZ = (int) cameraZ;
			ctx.minLevel = minLevel;
			ctx.level = level;
			ctx.maxLevel = maxLevel;
			ctx.hideRoofIds = hideRoofIds;
		}

		if (scene.getWorldViewId() == WorldView.TOPLEVEL)
		{
			this.cameraX = cameraX;
			this.cameraY = cameraY;
			this.cameraZ = cameraZ;
			this.cameraYaw = client.getCameraYaw();
			this.cameraPitch = client.getCameraPitch();
			this.cameraFpYaw = client.getCameraFpYaw();
			this.cameraFpPitch = client.getCameraFpPitch();
			preSceneDrawToplevel(scene, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw);
		}
		else
		{
			Scene toplevel = client.getScene();
			vaoO.addRange(null, toplevel);
			vaoPO.addRange(null, toplevel);
			glUniform4i(uniEntityTint, scene.getOverrideHue(), scene.getOverrideSaturation(), scene.getOverrideLuminance(), scene.getOverrideAmount());
		}
	}

	private void preSceneDrawToplevel(Scene scene,
		float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw)
	{
		scene.setDrawDistance(getDrawDistance());

		hudHelper.swap(client);
		targetOutlineBufferOffset = 0;

		// UBO
		uniformBuffer.clear();
		uniformBuffer
			.put(cameraYaw)
			.put(cameraPitch)
			.put(cameraX)
			.put(cameraY)
			.put(cameraZ);
		uniformBuffer.flip();

		glBindBuffer(GL_UNIFORM_BUFFER, glUniformBuffer.glBufferId);
		glBufferData(GL_UNIFORM_BUFFER, uniformBuffer.getBuffer(), GL_DYNAMIC_DRAW);
		glBindBuffer(GL_UNIFORM_BUFFER, 0);
		uniformBuffer.clear();

		glBindBufferBase(GL_UNIFORM_BUFFER, 0, glUniformBuffer.glBufferId);

		checkGLErrors();

		final int canvasHeight = client.getCanvasHeight();
		final int canvasWidth = client.getCanvasWidth();

		final int viewportHeight = client.getViewportHeight();
		final int viewportWidth = client.getViewportWidth();

		// Setup FBO and anti-aliasing
		/*{
			final AntiAliasingMode antiAliasingMode = config.antiAliasingMode();
			final Dimension stretchedDimensions = client.getStretchedDimensions();

			final int stretchedCanvasWidth = client.isStretchedEnabled() ? stretchedDimensions.width : canvasWidth;
			final int stretchedCanvasHeight = client.isStretchedEnabled() ? stretchedDimensions.height : canvasHeight;

			// Re-create fbo
			if (lastStretchedCanvasWidth != stretchedCanvasWidth
				|| lastStretchedCanvasHeight != stretchedCanvasHeight
				|| lastAntiAliasingMode != antiAliasingMode)
			{
				shutdownFbo();

				// Bind default FBO to check whether anti-aliasing is forced
				glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
				final int forcedAASamples = glGetInteger(GL_SAMPLES);
				final int maxSamples = glGetInteger(GL_MAX_SAMPLES);
				final int samples = forcedAASamples != 0 ? forcedAASamples :
					Math.min(antiAliasingMode.getSamples(), maxSamples);

				log.debug("AA samples: {}, max samples: {}, forced samples: {}", samples, maxSamples, forcedAASamples);

				initFbo(stretchedCanvasWidth, stretchedCanvasHeight, samples);

				lastStretchedCanvasWidth = stretchedCanvasWidth;
				lastStretchedCanvasHeight = stretchedCanvasHeight;
				lastAntiAliasingMode = antiAliasingMode;
			}

			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fboScene);
		}*/
		glBindFramebuffer(GL_FRAMEBUFFER, swapchainFramebuffer);

		// Clear scene
		int sky = client.getSkyboxColor();
		glClearColor((sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		glClearDepth(1d);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// Setup anisotropic filtering
		final int anisotropicFilteringLevel = config.anisotropicFilteringLevel();

		if (textureArrayId != -1 && lastAnisotropicFilteringLevel != anisotropicFilteringLevel)
		{
			textureManager.setAnisotropicFilteringLevel(textureArrayId, anisotropicFilteringLevel);
			lastAnisotropicFilteringLevel = anisotropicFilteringLevel;
		}

		// Setup viewport
		int renderWidthOff = client.getViewportXOffset();
		int renderHeightOff = client.getViewportYOffset();
		int renderCanvasHeight = canvasHeight;
		int renderViewportHeight = viewportHeight;
		int renderViewportWidth = viewportWidth;
		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			renderCanvasHeight = dim.height;

			double scaleFactorY = dim.getHeight() / canvasHeight;
			double scaleFactorX = dim.getWidth() / canvasWidth;

			// Pad the viewport a little because having ints for our viewport dimensions can introduce off-by-one errors.
			final int padding = 1;

			// Ceil the sizes because even if the size is 599.1 we want to treat it as size 600 (i.e. render to the x=599 pixel).
			renderViewportHeight = (int) Math.ceil(scaleFactorY * (renderViewportHeight)) + padding * 2;
			renderViewportWidth = (int) Math.ceil(scaleFactorX * (renderViewportWidth)) + padding * 2;

			// Floor the offsets because even if the offset is 4.9, we want to render to the x=4 pixel anyway.
			renderHeightOff = (int) Math.floor(scaleFactorY * (renderHeightOff)) - padding;
			renderWidthOff = (int) Math.floor(scaleFactorX * (renderWidthOff)) - padding;
		}

		//glDpiAwareViewport(renderWidthOff, renderCanvasHeight - renderViewportHeight - renderHeightOff, renderViewportWidth, renderViewportHeight);

		glUseProgram(glProgram);
		// Setup uniforms
		final int drawDistance = getDrawDistance();
		final int fogDepth = config.fogDepth();
		glUniform1i(uniUseFog, fogDepth > 0 ? 1 : 0);
		glUniform4f(uniFogColor, (sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		glUniform1i(uniFogDepth, fogDepth);
		glUniform1i(uniDrawDistance, drawDistance * Perspective.LOCAL_TILE_SIZE);
		glUniform1i(uniExpandedMapLoadingChunks, client.getExpandedMapLoading());
		glUniform1f(uniColorblindIntensity, config.colorBlindIntensity());

		// Brightness happens to also be stored in the texture provider, so we use that
		TextureProvider textureProvider = client.getTextureProvider();
		glUniform1f(uniBrightness, (float) textureProvider.getBrightness());
		glUniform1f(uniSmoothBanding, config.smoothBanding() ? 0f : 1f);
		glUniform1f(uniTextureLightMode, config.brightTextures() ? 1f : 0f);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// avoid textures animating during loading
			glUniform1i(uniTick, client.getGameCycle() & 127);
		}

		// Calculate projection matrix
		float[] projectionMatrix = Mat4.scale(client.getScale(), client.getScale(), -1);
		com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.projection(viewportWidth, viewportHeight, 250));
		com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
		com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateY((float) cameraYaw));
		com.vr.Mat4.mul(projectionMatrix, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));
		glUniformMatrix4fv(uniWorldProj, false, projectionMatrix);

		projectionMatrix = Mat4.identity();
		glUniformMatrix4fv(uniEntityProj, false, projectionMatrix);
		glUniformMatrix4fv(uniOutlineEntityProj, false, projectionMatrix);

		glUniform4i(uniEntityTint, 0, 0, 0, 0);

		// Bind uniforms
		glUniformBlockBinding(glProgram, uniBlockMain, 0);
		glUniform1i(uniTextures, 1); // texture sampler array is bound to texture1

		// Enable face culling
		glEnable(GL_CULL_FACE);

		// Enable blending
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

		// Enable depth testing
		glDepthFunc(GL_LESS);//GREATER);
		glEnable(GL_DEPTH_TEST);

		renderFrameOpenXR(0,0,GameState.LOGGED_IN,0,viewportWidth,viewportHeight);

		checkGLErrors();
	}

	int overlayColor = 0;
	@Override
	public void postSceneDraw(Scene scene)
	{
		if (scene.getWorldViewId() == WorldView.TOPLEVEL)
		{
			outlineVertexBuffer.flip();
			IntBuffer outlineVertexBuffer = this.outlineVertexBuffer.getBuffer();
			updateBuffer(tmpOutlineVertexBuffer, GL43C.GL_ARRAY_BUFFER, outlineVertexBuffer, GL43C.GL_DYNAMIC_DRAW, 0L);
			perEyeRender(() -> OpenGLRenderView2(client.getViewportWidth(), client.getViewportHeight(), overlayColor));
			postDrawToplevel();
			foundHoverRender = false;
			foundInteractRender = false;
		}
		else
		{
			glUniform4i(uniEntityTint, 0, 0, 0, 0);
		}
	}

	private void postDrawToplevel()
	{
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
		glDisable(GL_DEPTH_TEST);

		//glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glFlush();

		if(xRviews.size() > 0) {
			try (MemoryStack stack = stackPush()) {
				for (int viewIndex = 0; viewIndex < viewCountOutput; viewIndex++) {
					// Each view has a separate swapchain which is acquired, rendered to, and released.
					VRPlugin.Swapchain viewSwapchain = swapchains[viewIndex];
					check(xrReleaseSwapchainImage(
							viewSwapchain.handle,
							XrSwapchainImageReleaseInfo.calloc(stack)
									.type$Default()
					));
				}
				check(xrEndFrame(
						xrSession,
						XrFrameEndInfo.malloc(stack)
								.type$Default()
								.next(NULL)
								.displayTime(displayTime)
								.environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
								.layers(didRender ? layers : null)
								.layerCount(didRender ? layers.remaining() : 0)
				));
			}
			xRviews.clear();
			xRimages.clear();
		} //else {
			//System.out.println("NOT");
		//}
		//glBindFramebuffer(GL_DRAW_FRAMEBUFFER, awtContext.getFramebuffer(false));
		this.outlineVertexBuffer.clear();
		sceneFboValid = true;
	}

	private void blitSceneFbo()
	{
		int width = lastStretchedCanvasWidth;
		int height = lastStretchedCanvasHeight;

		final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
		final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

		width = getScaledValue(transform.getScaleX(), width);
		height = getScaledValue(transform.getScaleY(), height);

		int defaultFbo = awtContext.getFramebuffer(false);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, fboScene);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, defaultFbo);
		glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
			GL_COLOR_BUFFER_BIT, GL_NEAREST);

		// Reset
		glBindFramebuffer(GL_READ_FRAMEBUFFER, defaultFbo);

		checkGLErrors();
	}

	@Override
	public void drawZoneOpaque(Projection entityProjection, Scene scene, int zx, int zz)
	{
		updateEntityProjection(entityProjection);

		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		Zone z = ctx.zones[zx][zz];
		if (!z.initialized)
		{
			return;
		}

		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
		z.renderOpaque(this, zx - offset, zz - offset, ctx.minLevel, ctx.level, ctx.maxLevel, ctx.hideRoofIds);

		checkGLErrors();
	}

	private static final int ALPHA_ZSORT_CLOSE = 2048;

	@Override
	public void drawZoneAlpha(Projection entityProjection, Scene scene, int level, int zx, int zz)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		// this is a noop after the first zone
		vaoA.unmap();

		Zone z = ctx.zones[zx][zz];
		if (!z.initialized)
		{
			return;
		}

		updateEntityProjection(entityProjection);
		glUniform4i(uniEntityTint, scene.getOverrideHue(), scene.getOverrideSaturation(), scene.getOverrideLuminance(), scene.getOverrideAmount());

		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
		int dx = ctx.cameraX - ((zx - offset) << 10);
		int dz = ctx.cameraZ - ((zz - offset) << 10);
		boolean close = dx * dx + dz * dz < ALPHA_ZSORT_CLOSE * ALPHA_ZSORT_CLOSE;

		if (level == 0)
		{
			z.alphaSort(zx - offset, zz - offset, ctx.cameraX, ctx.cameraY, ctx.cameraZ);
			z.multizoneLocs(scene, zx - offset, zz - offset, ctx.cameraX, ctx.cameraZ, ctx.zones);
		}

		z.renderAlpha(this, zx - offset, zz - offset, cameraYaw, cameraPitch, ctx.minLevel, ctx.level, ctx.maxLevel, level, ctx.hideRoofIds, !close || (scene.getOverrideAmount() > 0));

		checkGLErrors();
	}

	@Override
	public void drawPass(Projection projection, Scene scene, int pass)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		updateEntityProjection(projection);

		if (pass == DrawCallbacks.PASS_OPAQUE)
		{
			vaoO.addRange(projection, scene);
			vaoPO.addRange(projection, scene);

			if (scene.getWorldViewId() == WorldView.TOPLEVEL)
			{
				glUniform3i(uniBase, 0, 0, 0);

				int sz = vaoO.unmap();
				for (int i = 0; i < sz; ++i)
				{
					VAO vao = vaoO.vaos.get(i);
					vao.draw(this);
					vao.reset();
				}

				sz = vaoPO.unmap();
				if (sz > 0)
				{
					glDepthMask(false);
					for (int i = 0; i < sz; ++i)
					{
						VAO vao = vaoPO.vaos.get(i);
						vao.draw(this);
					}
					glDepthMask(true);

					glColorMask(false, false, false, false);
					for (int i = 0; i < sz; ++i)
					{
						VAO vao = vaoPO.vaos.get(i);
						vao.draw(this);
						vao.reset();
					}
					glColorMask(true, true, true, true);
				}
			}
		}
		else if (pass == DrawCallbacks.PASS_ALPHA)
		{
			for (int x = 0; x < ctx.sizeX; ++x)
			{
				for (int z = 0; z < ctx.sizeZ; ++z)
				{
					Zone zone = ctx.zones[x][z];
					zone.removeTemp();
				}
			}
		}

		checkGLErrors();
	}

	@Override
	public void drawDynamic(Projection worldProjection, Scene scene, TileObject tileObject, Renderable r, Model m, int orient, int x, int y, int z)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		if (!renderCallbackManager.drawObject(scene, tileObject))
		{
			return;
		}

		/*Model model;
		if (r instanceof Model)
		{
			model = (Model) r;
		}
		else
		{
			model = r.getModel();
			if (model == null)
			{
				return;
			}
		}

		if (model != r)
		{
			r.setModelHeight(model.getModelHeight());
		}*/

		/*if (r instanceof Actor){
			hudHelper.backloadActor((Actor)r, orient, x & 1023, y, z & 1023);
		}*/

		int size = m.getFaceCount() * 3 * VAO.VERT_SIZE;
		if (m.getFaceTransparencies() == null)
		{
			VAO o = vaoO.get(size);
			clientUploader.uploadTempModel(m, orient, x, y, z, o.vbo.vb);
		}
		else
		{
			m.calculateBoundsCylinder();
			VAO o = vaoO.get(size), a = vaoA.get(size);
			int start = a.vbo.vb.position();
			try
			{
				facePrioritySorter.uploadSortedModel(worldProjection, m, orient, x, y, z, o.vbo.vb, a.vbo.vb, false);
			}
			catch (Exception ex)
			{
				log.debug("error drawing entity", ex);
			}
			int end = a.vbo.vb.position();

			if (end > start)
			{
				int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? SCENE_OFFSET : 0;
				int zx = (x >> 10) + (offset >> 3);
				int zz = (z >> 10) + (offset >> 3);
				Zone zone = ctx.zones[zx][zz];

				// level is checked prior to this callback being run, in order to cull clickboxes, but
				// tileObject.getPlane()>maxLevel if visbelow is set - lower the object to the max level
				int plane = Math.min(ctx.maxLevel, tileObject.getPlane());
				// renderable modelheight is typically not set here because DynamicObject doesn't compute it on the returned model
				zone.addTempAlphaModel(a.vao, start, end, plane, x & 1023, y, z & 1023);
			}
		}
	}

	/**ACTORS, THINGS THAT MOVE, ETC.**/
	@Override
	public void drawTemp(Projection worldProjection, Scene scene, GameObject gameObject, Model m, int orient, int x, int y, int z)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		if (!renderCallbackManager.drawObject(scene, gameObject))
		{
			return;
		}

		Renderable renderable = gameObject.getRenderable();

		if (renderable instanceof Actor){
			hudHelper.backloadActor((Actor)renderable, orient, x, y, z);
		}

		if(!foundHoverRender) {
			renderMouseover(worldProjection, orient, x, y, z, renderable);
		}
		if(!foundInteractRender) {
			renderTarget(worldProjection, orient, x, y, z, renderable);
		}

		//System.out.println(cameraX+" "+cameraY+" "+cameraZ+" "+cameraYaw+" "+cameraPitch);

		int size = m.getFaceCount() * 3 * VAO.VERT_SIZE;
		int renderMode = renderable.getRenderMode();
		if (renderMode == Renderable.RENDERMODE_SORTED_NO_DEPTH || m.getFaceTransparencies() != null)
		{
			// opaque player faces have their own vao and are drawn in a separate pass from normal opaque faces
			// because they are not depth tested. transparent player faces don't need their own vao because normal
			// transparent faces are already not depth tested
			VAO o = renderMode == Renderable.RENDERMODE_SORTED_NO_DEPTH ? vaoPO.get(size) : vaoO.get(size);
			VAO a = vaoA.get(size);

			int start = a.vbo.vb.position();
			m.calculateBoundsCylinder();
			try
			{
				facePrioritySorter.uploadSortedModel(worldProjection, m, orient, x, y, z, o.vbo.vb, a.vbo.vb, renderMode == Renderable.RENDERMODE_SORTED_NO_DEPTH);
			}
			catch (Exception ex)
			{
				log.debug("error drawing entity", ex);
			}
			int end = a.vbo.vb.position();

			if (end > start)
			{
				int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
				int zx = (gameObject.getX() >> 10) + offset;
				int zz = (gameObject.getY() >> 10) + offset;
				Zone zone = ctx.zones[zx][zz];
				int plane = Math.min(ctx.maxLevel, gameObject.getPlane());
				zone.addTempAlphaModel(a.vao, start, end, plane, x & 1023, y - renderable.getModelHeight() /* to render players over locs */, z & 1023);
			}
		}
		else
		{
			VAO o = vaoO.get(size);
			clientUploader.uploadTempModel(m, orient, x, y, z, o.vbo.vb);
		}
	}

	@Override
	public void invalidateZone(Scene scene, int zx, int zz)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		Zone z = ctx.zones[zx][zz];
		if (!z.invalidate)
		{
			z.invalidate = true;
			log.debug("Zone invalidated: wx={} x={} z={}", scene.getWorldViewId(), zx, zz);
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}

		rebuild(wv);
		for (WorldEntity we : wv.worldEntities())
		{
			wv = we.getWorldView();
			rebuild(wv);
		}
	}

	private void rebuild(WorldView wv)
	{
		SceneContext ctx = context(wv);
		if (ctx == null)
		{
			return;
		}

		for (int x = 0; x < ctx.sizeX; ++x)
		{
			for (int z = 0; z < ctx.sizeZ; ++z)
			{
				Zone zone = ctx.zones[x][z];
				if (!zone.invalidate)
				{
					continue;
				}

				assert zone.initialized;
				zone.free();
				zone = ctx.zones[x][z] = new Zone();

				Scene scene = wv.getScene();
				clientUploader.zoneSize(scene, zone, x, z);

				VBO o = null, a = null;
				int sz = zone.sizeO * Zone.VERT_SIZE * 3;
				if (sz > 0)
				{
					o = new VBO(sz);
					o.init(GL_STATIC_DRAW);
					o.map();
				}

				sz = zone.sizeA * Zone.VERT_SIZE * 3;
				if (sz > 0)
				{
					a = new VBO(sz);
					a.init(GL_STATIC_DRAW);
					a.map();
				}

				zone.init(o, a);

				clientUploader.uploadZone(scene, zone, x, z);

				zone.unmap();
				zone.initialized = true;
				zone.dirty = true;

				log.debug("Rebuilt zone wv={} x={} z={}", wv.getId(), x, z);
			}
		}
	}

	private void prepareInterfaceTexture(int canvasWidth, int canvasHeight)
	{
		if (canvasWidth != lastCanvasWidth || canvasHeight != lastCanvasHeight)
		{
			lastCanvasWidth = canvasWidth;
			lastCanvasHeight = canvasHeight;

			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, interfacePbo);
			glBufferData(GL_PIXEL_UNPACK_BUFFER, canvasWidth * canvasHeight * 4L, GL_STREAM_DRAW);
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

			glBindTexture(GL_TEXTURE_2D, interfaceTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, canvasWidth, canvasHeight, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0);
			glBindTexture(GL_TEXTURE_2D, 0);
		}

		final BufferProvider bufferProvider = client.getBufferProvider();
		final int[] pixels = bufferProvider.getPixels();
		final int width = bufferProvider.getWidth();
		final int height = bufferProvider.getHeight();

		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, interfacePbo);
		ByteBuffer interfaceBuf = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
		if (interfaceBuf != null)
		{
			interfaceBuf
				.asIntBuffer()
				.put(pixels, 0, width * height);
			glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
		}
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	int lastMenuWidth = -1;
	int lastMenuHeight = -1;
	//TODO:REIMPLEMENT THIS
	private void prepareMenuTexture(int menuWidth, int menuHeight)
	{
		if (menuWidth != lastMenuWidth || menuHeight != lastMenuHeight)
		{
			lastMenuWidth = menuWidth;
			lastMenuHeight = menuHeight;
			GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, menuPbo);
			GL43C.glBufferData(GL43C.GL_PIXEL_UNPACK_BUFFER, menuWidth * menuHeight * 4L, GL43C.GL_STREAM_DRAW);
			GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, 0);

			GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, menuTexture);
			GL43C.glTexImage2D(GL43C.GL_TEXTURE_2D, 0, GL43C.GL_RGBA, menuWidth, menuHeight, 0, GL43C.GL_BGRA, GL43C.GL_UNSIGNED_BYTE, 0);
			GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
		}

		final BufferProvider bufferProvider = client.getBufferProvider();
		final int[] pixels = bufferProvider.getPixels();
		final int[] unpackPixels = new int[menuWidth*menuHeight];
		int k = 0;
		for(int i = client.getMenuY(); i < client.getMenuY()+menuHeight; i++){
			for(int j = client.getMenuX(); j < client.getMenuX()+menuWidth; j++){
				if(lastCanvasWidth*i+j < lastCanvasWidth*lastCanvasHeight) {
					unpackPixels[k++] = pixels[lastCanvasWidth * i + j];
				} else {
					unpackPixels[k++] = 0;
				}
			}
		}

		GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, menuPbo);
		GL43C.glMapBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, GL43C.GL_WRITE_ONLY)
				.asIntBuffer()
				.put(unpackPixels, 0, menuWidth*menuHeight);
		GL43C.glUnmapBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, menuTexture);
		GL43C.glTexSubImage2D(GL43C.GL_TEXTURE_2D, 0, 0, 0, menuWidth, menuHeight, GL43C.GL_BGRA, GL43C.GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, 0);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
	}

	private boolean renderFrameOpenXR(int sky, float brightness, GameState gameState,int overlayColor, float viewportWidth, float viewportHeight) {
		if(pollEvents() == ENDING_POLLED) return false;
		try (MemoryStack stack = stackPush()) {
			XrFrameState frameState = XrFrameState.calloc(stack)
					.type$Default();

			check(xrWaitFrame(
					xrSession,
					XrFrameWaitInfo.calloc(stack)
							.type$Default(),
					frameState
			));

			check(xrSyncActions(
					xrSession,
					XrActionsSyncInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.activeActionSets(
									XrActiveActionSet
											.malloc(2)
											.put(0,XrActiveActionSet.malloc(stack).actionSet(xrActionSet).subactionPath(leftHandPath))
											.put(1,XrActiveActionSet.malloc(stack).actionSet(xrActionSet).subactionPath(rightHandPath))
							)
			));

			XrActionStatePose poseL = XrActionStatePose.malloc(stack).type$Default().next(NULL);
			XrActionStatePose poseR = XrActionStatePose.malloc(stack).type$Default().next(NULL);
			check(xrGetActionStatePose(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(pose)
							.subactionPath(leftHandPath),
					poseL
			));
			check(xrGetActionStatePose(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(pose)
							.subactionPath(rightHandPath),
					poseR
			));
			if(poseL.isActive()){
				XrSpaceLocation locL = XrSpaceLocation.malloc(stack).type$Default().next(NULL);
				check(xrLocateSpace(leftHandSpace, xrAppSpace, frameState.predictedDisplayTime(), locL));
				leftPose = locL.pose();
			}
			if(poseR.isActive()){
				XrSpaceLocation locR = XrSpaceLocation.malloc(stack).type$Default().next(NULL);
				check(xrLocateSpace(rightHandSpace, xrAppSpace, frameState.predictedDisplayTime(), locR));
				rightPose = locR.pose();
			}

			XrActionStateBoolean lClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			XrActionStateBoolean rClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			XrActionStateBoolean mClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			XrActionStateBoolean aClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			XrActionStateBoolean bClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			XrActionStateBoolean xClick = XrActionStateBoolean.malloc(stack).type$Default().next(NULL);
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(leftClick)
							.subactionPath(rightHandPath),
					lClick
			));
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(rightClick)
							.subactionPath(rightHandPath),
					rClick
			));
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(middleClick)
							.subactionPath(rightHandPath),
					mClick
			));
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(aButton)
							.subactionPath(rightHandPath),
					aClick
			));
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(bButton)
							.subactionPath(rightHandPath),
					bClick
			));
			check(xrGetActionStateBoolean(
					xrSession,
					XrActionStateGetInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.action(xButton)
							.subactionPath(leftHandPath),
					xClick
			));
			if(lClick.changedSinceLastSync()){
				if(state != HandSelectState.OUT_OF_BOUNDS || hovering)
					robot.leftClick(lClick.currentState());
			}
			if(rClick.changedSinceLastSync()){
				if(state != HandSelectState.OUT_OF_BOUNDS || hovering)
					robot.rightClick(rClick.currentState());
			}
			if(xClick.changedSinceLastSync()){
				if(!xClick.currentState()){
					//TODO:REIMPLEMENT THIS
					mapVisible = !mapVisible;
				}
			}
			if(mClick.changedSinceLastSync()){
				if(state != HandSelectState.SELECTING)
					robot.middleClick(mClick.currentState());
			}
			if(aClick.changedSinceLastSync()){
				if(state == HandSelectState.SELECTING)
					robot.selectDown(aClick.currentState());
			}
			if(bClick.changedSinceLastSync()){
				if(state == HandSelectState.SELECTING)
					robot.selectUp(bClick.currentState());
			}

			check(xrBeginFrame(
					xrSession,
					XrFrameBeginInfo.calloc(stack)
							.type$Default()
			));

			XrCompositionLayerProjection layerProjection = XrCompositionLayerProjection.calloc(stack)
					.type$Default();

			layers = stack.callocPointer(1);

			didRender = false;
			//System.out.println(frameState.shouldRender()+" "+frameState.predictedDisplayTime());
			if (frameState.shouldRender()) {
				if (renderLayerOpenXR(sky, brightness, gameState, stack, frameState.predictedDisplayTime(), layerProjection, viewportWidth, viewportHeight, overlayColor)) {
					layers.put(0, layerProjection);
					didRender = true;
				} else {
					System.out.println("Didn't render");
				}
			} else {
				System.out.println("Shouldn't render");
			}

			displayTime = frameState.predictedDisplayTime();
			//System.out.println(stack.getFrameIndex()+" "+stack.getSize()+" "+stack.getAddress());

			/*check(xrEndFrame(
					xrSession,
					XrFrameEndInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.displayTime(frameState.predictedDisplayTime())
							.environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
							.layers(didRender ? layers : null)
							.layerCount(didRender ? layers.remaining() : 0)
			));*/
		}
		return true;
	}

	long displayTime = 0;
	PointerBuffer layers = null;
	boolean didRender = false;

	private boolean eye = true;

	private boolean renderLayerOpenXR(int sky, float brightness, GameState gameState, MemoryStack stack, long predictedDisplayTime, XrCompositionLayerProjection layer, float viewportWidth, float viewportHeight, int overlayColor) {
		XrViewState viewState = XrViewState.calloc(stack)
				.type$Default();

		IntBuffer pi = stack.mallocInt(1);
		check(xrLocateViews(
				xrSession,
				XrViewLocateInfo.malloc(stack)
						.type$Default()
						.next(NULL)
						.viewConfigurationType(viewConfigType)
						.displayTime(predictedDisplayTime)
						.space(xrAppSpace),
				viewState,
				pi,
				views
		));

		if ((viewState.viewStateFlags() & XR_VIEW_STATE_POSITION_VALID_BIT) == 0 ||
				(viewState.viewStateFlags() & XR_VIEW_STATE_ORIENTATION_VALID_BIT) == 0) {
			return false;  // There is no valid tracking poses for the views.
		}

		viewCountOutput = pi.get(0);
		assert (viewCountOutput == views.capacity());
		assert (viewCountOutput == viewConfigs.capacity());
		assert (viewCountOutput == swapchains.length);

		XrCompositionLayerProjectionView.Buffer projectionLayerViews = XRHelper.fill(
				XrCompositionLayerProjectionView.calloc(viewCountOutput, stack),
				XrCompositionLayerProjectionView.TYPE,
				XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW
		);

		// Render view to the appropriate part of the swapchain image.
		for (int viewIndex = 0; viewIndex < viewCountOutput; viewIndex++) {
			// Each view has a separate swapchain which is acquired, rendered to, and released.
			VRPlugin.Swapchain viewSwapchain = swapchains[viewIndex];

			check(xrAcquireSwapchainImage(
					viewSwapchain.handle,
					XrSwapchainImageAcquireInfo.calloc(stack)
							.type$Default(),
					pi
			));
			int swapchainImageIndex = pi.get(0);

			check(xrWaitSwapchainImage(
					viewSwapchain.handle,
					XrSwapchainImageWaitInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.timeout(XR_INFINITE_DURATION)
			));

			XrCompositionLayerProjectionView projectionLayerView = projectionLayerViews.get(viewIndex)
					.pose(views.get(viewIndex).pose())
					.fov(views.get(viewIndex).fov())
					.subImage(si -> si
							.swapchain(viewSwapchain.handle)
							.imageRect(rect -> rect
									.offset(offset -> offset
											.x(0)
											.y(0))
									.extent(extent -> extent
											.width(viewSwapchain.width)
											.height(viewSwapchain.height)
									)));

			xRviews.add(projectionLayerView);
			xRimages.add(viewSwapchain.images.get(swapchainImageIndex));

			OpenGLClearView(projectionLayerView,viewSwapchain.images.get(swapchainImageIndex));
			//OpenGLRenderView(sky, brightness, gameState,projectionLayerView, viewSwapchain.images.get(swapchainImageIndex), viewIndex, viewportWidth, viewportHeight, overlayColor);

			/*check(xrReleaseSwapchainImage(
					viewSwapchain.handle,
					XrSwapchainImageReleaseInfo.calloc(stack)
							.type$Default()
			));*/
		}

		layer.space(xrAppSpace);
		layer.views(projectionLayerViews);
		return true;
	}

	int viewCountOutput = 0;
	ArrayList<XrCompositionLayerProjectionView> xRviews = new ArrayList<>();
	ArrayList<XrSwapchainImageOpenGLKHR> xRimages = new ArrayList<>();

	@Subscribe
	void onWorldChanged(WorldChanged worldChanged){
		//TODO:REIMPLEMENT THIS
		forceMap = false;
		hudHelper.cullAll();
	}

	private static FloatBuffer mvpMatrix = BufferUtils.createFloatBuffer(16);
	//int screenShader = ShadersGL.createShaderProgram(ShadersGL.screenVertShader, ShadersGL.texFragShader);

	private boolean hovering = false;

	@FunctionalInterface
	interface RenderOperation {
		void execute();
	}

	void perEyeRender(RenderOperation operation){
		for(int i = 0; i < xRviews.size(); i++){
			OpenGLRenderView(xRviews.get(i),xRimages.get(i), operation);
		}
	}

	private void OpenGLClearView(XrCompositionLayerProjectionView layerView, XrSwapchainImageOpenGLKHR swapchainImage) {
		glBindFramebuffer(GL_FRAMEBUFFER, swapchainFramebuffer);

		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, swapchainImage.image(), 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextures.get(swapchainImage), 0);

		XrRect2Di imageRect = layerView.subImage().imageRect();
		//System.out.println(imageRect.offset().x()+" "+imageRect.offset().y()+" "+imageRect.extent().width()+" "+imageRect.extent().height());
		glViewport(
				imageRect.offset().x(),
				imageRect.offset().y(),
				imageRect.extent().width(),
				imageRect.extent().height()
		);

		float[] DarkSlateGray = {0.184313729f, 0.309803933f, 0.309803933f};
		glClearColor(DarkSlateGray[0], DarkSlateGray[1], DarkSlateGray[2], 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	}

	private void OpenGLRenderView(XrCompositionLayerProjectionView layerView, XrSwapchainImageOpenGLKHR swapchainImage, RenderOperation operation) {
		/*GL43C.glUseProgram(glProgram);

		final int drawDistance = getDrawDistance();
		final int fogDepth = config.fogDepth();
		glUniform1i(uniUseFog, fogDepth > 0 ? 1 : 0);
		glUniform4f(uniFogColor, (sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		glUniform1i(uniFogDepth, fogDepth);
		glUniform1i(uniDrawDistance, drawDistance * Perspective.LOCAL_TILE_SIZE);
		glUniform1i(uniExpandedMapLoadingChunks, client.getExpandedMapLoading());
		glUniform1f(uniColorblindIntensity, config.colorBlindIntensity());

		// Brightness happens to also be stored in the texture provider, so we use that
		TextureProvider textureProvider = client.getTextureProvider();
		glUniform1f(uniBrightness, (float) textureProvider.getBrightness());
		glUniform1f(uniSmoothBanding, config.smoothBanding() ? 0f : 1f);
		glUniform1f(uniTextureLightMode, config.brightTextures() ? 1f : 0f);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// avoid textures animating during loading
			glUniform1i(uniTick, client.getGameCycle() & 127);
		}

		float[] projectionMatrixE = Mat4.identity();
		glUniformMatrix4fv(uniEntityProj, false, projectionMatrixE);

		glUniform4i(uniEntityTint, 0, 0, 0, 0);

		// Bind uniforms
		glUniformBlockBinding(glProgram, uniBlockMain, 0);
		glUniform1i(uniTextures, 1); // texture sampler array is bound to texture1

		// We just allow the GL to do face culling. Note this requires the priority renderer
		// to have logic to disregard culled faces in the priority depth testing.
		// Enable face culling
		glEnable(GL_CULL_FACE);

		// Enable blending
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

		// Enable depth testing
		glDepthFunc(GL_GREATER);
		glEnable(GL_DEPTH_TEST);*/


		/*THE ACTUAL START OF THE CODE*/
		glBindFramebuffer(GL_FRAMEBUFFER, swapchainFramebuffer);

		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, swapchainImage.image(), 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextures.get(swapchainImage), 0);

		XrRect2Di imageRect = layerView.subImage().imageRect();
		//System.out.println(imageRect.offset().x()+" "+imageRect.offset().y()+" "+imageRect.extent().width()+" "+imageRect.extent().height());
		glViewport(
				imageRect.offset().x(),
				imageRect.offset().y(),
				imageRect.extent().width(),
				imageRect.extent().height()
		);

		//float[] DarkSlateGray = {0.184313729f, 0.309803933f, 0.309803933f};
		//glClearColor(DarkSlateGray[0], DarkSlateGray[1], DarkSlateGray[2], 1.0f);
		//glClearDepth(1.0f);
		//glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);// | GL_STENCIL_BUFFER_BIT);

		//glFrontFace(GL_CW);
		//glCullFace(GL_BACK);
		//glEnable(GL_DEPTH_TEST);

		XrPosef pose = layerView.pose();
		XrVector3f pos = pose.position$();
		XrQuaternionf orientation = pose.orientation();

		glDisable(GL_CULL_FACE); // Disable back-face culling so we can see the inside of the world-space cube and backside of the plane

		XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 10000f, false);
		//System.out.println("("+pos.x()+","+pos.y()+","+pos.z()+")"+"("+orientation.x()+","+orientation.y()+","+orientation.z()+","+orientation.w()+")");
		viewMatrix.translationRotateScaleInvert(
				(float) pos.x(), (float) pos.y(), (float) pos.z(),
				orientation.x(), orientation.y(), orientation.z(), orientation.w(),
				1, 1, 1
		);

		//GL43C.glUniformMatrix4fv(uniWorldProj, false, projectionMatrix2);
		GL43C.glUniformMatrix4fv(uniView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniProjection, false, projectionMatrix.get(mvpMatrix));

		operation.execute();
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		//GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 0, targetBufferOffset);
	}

	private void OpenGLRenderView2(float viewportWidth, float viewportHeight, int overlayColor) {
		float[] projectionMatrix2 = Mat4.scale(client.getScale(), client.getScale(), -1);
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.projection(viewportWidth, viewportHeight, 250));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateX( (float)-(Math.PI-cameraFpPitch)));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateY((float)(cameraFpYaw)));
		com.vr.Mat4.mul(projectionMatrix2, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));

		if(rightPose != null) {
			handMatrix.translation(rightPose.position$().x(), (float) rightPose.position$().y(), (float) rightPose.position$().z())
					.rotate(new Quaternionf(rightPose.orientation().x(), rightPose.orientation().y(), rightPose.orientation().z(), rightPose.orientation().w()));

			Vector3f playAreaIntersect = CalcHelper.getPlayAreaIntersect(rightPose.position$(), rightPose.orientation());

			cursorMatrix.translation(playAreaIntersect.x(), playAreaIntersect.y(), playAreaIntersect.z());

			if(leftPose != null && (forceMap || mapVisible)) {
				mapMatrix.translation(leftPose.position$().x()+0.21f, (float) leftPose.position$().y()+0.21f, (float) leftPose.position$().z()-0.01f);
				Vector3f mapPlaneIntersect = CalcHelper.getMapPlaneIntersect(leftPose.position$(), leftPose.orientation(), rightPose.position$(), rightPose.orientation(), 0.21f, 0.21f, -0.01f);
				float dist = new Vector3f(leftPose.position$().x(),leftPose.position$().y(),leftPose.position$().z()).add(new Vector3f(0.21f,0.21f,0.01f)).distance(new Vector3f(rightPose.position$().x(),rightPose.position$().y(),rightPose.position$().z()));
				if(Math.abs(mapPlaneIntersect.x) <= 0.2 && Math.abs(mapPlaneIntersect.y) <= 0.2 && dist <= 0.22*Math.sqrt(2.0) && rightPose.position$().z()-(leftPose.position$().z()-0.01f)<0.05){
					hovering = true;
					robot.setCursorByMapPct((mapPlaneIntersect.x+0.2f)/0.4f, (mapPlaneIntersect.y+0.2f)/0.4f);
				} else {
					hovering = false;
				}
			} else {
				hovering = false;
			}

			if(!hovering){
				if (!client.isMenuOpen()) {
					state = HandSelectState.IDLE;
				} else {
					if (state != HandSelectState.SELECTING) {
						robot.startSelecting(client);
					}
					state = HandSelectState.SELECTING;
				}
				if (state != HandSelectState.SELECTING) {
					boolean inBounds = robot.setCursorByXY(playAreaIntersect.x(), playAreaIntersect.y());
					state = (!inBounds || !isTargetingWorld())? HandSelectState.OUT_OF_BOUNDS : HandSelectState.IDLE;
				}
			}
			glEnable(GL_BLEND);
			glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

			drawCursor(viewMatrix, handMatrix, cursorMatrix, projectionMatrix, state);

			GL43C.glDisable(GL43C.GL_BLEND);

			GL43C.glUseProgram(glOutlineProgram);

			glClear(GL_DEPTH_BUFFER_BIT);
			GL43C.glEnable(GL43C.GL_BLEND);
			GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA,GL43C.GL_ONE_MINUS_SRC_ALPHA);

			GL43C.glUniformMatrix4fv(uniOutlineProjectionMatrix, false, projectionMatrix2);
			GL43C.glUniformMatrix4fv(uniOutlineView, false, viewMatrix.get(mvpMatrix));
			GL43C.glUniformMatrix4fv(uniOutlineProjection, false, projectionMatrix.get(mvpMatrix));

			GL43C.glBindVertexArray(vaoOutlineTemp);
			GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 0, targetOutlineBufferOffset);

			GL43C.glDisable(GL43C.GL_BLEND);
			GL43C.glBindVertexArray(0);
			GL43C.glUseProgram(0);

			if(client.isMenuOpen() && !hovering){
				glClear(GL_DEPTH_BUFFER_BIT);
				drawMenu(overlayColor, client.getMenuWidth(), Math.min(lastCanvasHeight,client.getMenuHeight()), viewMatrix, projectionMatrix, projectionMatrix2, new Matrix4f());//mapMatrix);
			} else if(!client.isMenuOpen() && hintTarget != null && !hovering) {
				glClear(GL_DEPTH_BUFFER_BIT);
				hudHelper.drawHint(overlayColor, viewMatrix, projectionMatrix, projectionMatrix2, hintTileX, hintTileY, hintAction, hintTarget, hintActor, hintIntersect);
			}

			glClear(GL_DEPTH_BUFFER_BIT);
			hudHelper.drawHud(viewMatrix, projectionMatrix, projectionMatrix2);

			glClear(GL_DEPTH_BUFFER_BIT);
			drawHand(viewMatrix, handMatrix, cursorMatrix, projectionMatrix, state);
			if(mapVisible || forceMap) {
				drawUi(overlayColor, 100, 100, viewMatrix, projectionMatrix, mapMatrix);//mapMatrix);
			}
		}

		//glEnable(GL_CULL_FACE);

		//glBindFramebuffer(GL_FRAMEBUFFER, 0);

		//if (viewIndex == swapchains.length - 1) {
		//	glFlush();
		//}
	}

	@Override
	public void draw(int overlayColor)
	{
		this.overlayColor = overlayColor;
		final GameState gameState = client.getGameState();
		if (gameState == GameState.STARTING)
		{
			return;
		}

		final TextureProvider textureProvider = client.getTextureProvider();
		if (textureArrayId == -1 && textureProvider != null)
		{
			// lazy init textures as they may not be loaded at plugin start.
			// this will return -1 and retry if not all textures are loaded yet, too.
			textureArrayId = textureManager.initTextureArray(textureProvider);
			if (textureArrayId > -1)
			{
				// if texture upload is successful, compute and set texture animations
				float[] texAnims = textureManager.computeTextureAnimations(textureProvider);
				glUseProgram(glProgram);
				glUniform2fv(uniTextureAnimations, texAnims);
				glUseProgram(0);
			}
		}

		final int canvasHeight = client.getCanvasHeight();
		final int canvasWidth = client.getCanvasWidth();

		prepareInterfaceTexture(canvasWidth, canvasHeight);
		if(client.isMenuOpen()) {
			final int menuHeight = Math.min(lastCanvasHeight,client.getMenuHeight());
			final int menuWidth = client.getMenuWidth();
			prepareMenuTexture(menuWidth, menuHeight);
		} else {
			generateHint();
		}

		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		/*if (sceneFboValid)
		{
			blitSceneFbo();
		}

		// Texture on UI
		drawUi(overlayColor, canvasHeight, canvasWidth);*/

		/*try
		{
			awtContext.swapBuffers();
		}
		catch (RuntimeException ex)
		{
			// this is always fatal
			if (!canvas.isValid())
			{
				// this might be AWT shutting down on VM shutdown, ignore it
				return;
			}

			log.error("error swapping buffers", ex);

			// try to stop the plugin
			SwingUtilities.invokeLater(() ->
			{
				try
				{
					pluginManager.stopPlugin(this);
				}
				catch (PluginInstantiationException ex2)
				{
					log.error("error stopping plugin", ex2);
				}
			});
			return;
		}*/

		drawManager.processDrawComplete(this::screenshot);

		//glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));

		checkGLErrors();
	}

	private void drawCursor(Matrix4f viewMatrix, Matrix4f handMatrix, Matrix4f cursorMatrix, Matrix4f projectionMatrix, HandSelectState state)
	{
		GL43C.glEnable(GL43C.GL_BLEND);
		// Use the texture bound in the first pass
		GL43C.glUseProgram(glHandProgram);
		GL43C.glUniformMatrix4fv(uniHandView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniCursor, false, cursorMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniHandProjection, false, projectionMatrix.get(mvpMatrix));
		if(hovering){
			GL43C.glUniform4f(uniHandColor, 255.0f,255.0f,255.0f,0.00f);
		} else {
			switch (state) {
				case IDLE:
					GL43C.glUniform4f(uniHandColor, 255.0f, 255.0f, 255.0f, 0.25f);
					break;
				case HOVERING:
					break;
				case SELECTING:
					GL43C.glUniform4f(uniHandColor, 255.0f, 255.0f, 255.0f, 0.00f);
					break;
				case OUT_OF_BOUNDS:
					GL43C.glUniform4f(uniHandColor, 255.0f, 0.0f, 0.0f, 0.25f);
					break;
			}
		}

		// Texture on UI
		GL43C.glBindVertexArray(vaoHandHandle);
		GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 0, 12);

		GL43C.glBindVertexArray(0);
		GL43C.glUseProgram(0);
		GL43C.glDisable(GL43C.GL_BLEND);
		//System.out.println(canvas.getLocationOnScreen());
	}

	boolean forceMap = false;
	boolean mapVisible = true;

	ArrayList<Integer> exemptWidgets = new ArrayList<>();
	{
		exemptWidgets.add(548);
		exemptWidgets.add(162);
		exemptWidgets.add(651);
		exemptWidgets.add(708);
		exemptWidgets.add(163);
		exemptWidgets.add(303);
		exemptWidgets.add(160);
		exemptWidgets.add(122);
		exemptWidgets.add(728);
		exemptWidgets.add(320);
		exemptWidgets.add(629);
		exemptWidgets.add(259);
		exemptWidgets.add(149);
		exemptWidgets.add(387);
		exemptWidgets.add(541);
		exemptWidgets.add(218);
		exemptWidgets.add(429);
		exemptWidgets.add(109);
		exemptWidgets.add(182);
		exemptWidgets.add(116);
		exemptWidgets.add(216);
		exemptWidgets.add(239);
		exemptWidgets.add(727);
		exemptWidgets.add(726);
		exemptWidgets.add(160);
		exemptWidgets.add(593);
		exemptWidgets.add(69);
		exemptWidgets.add(161);
		exemptWidgets.add(164);
		exemptWidgets.add(663);
		exemptWidgets.add(896);
		exemptWidgets.add(399);
		exemptWidgets.add(90);
		exemptWidgets.add(76);
		exemptWidgets.add(434);
	}

	HashSet<Integer> openWidgets = new HashSet<>();

	@Subscribe
	void onFocusChanged(FocusChanged focusChanged){
		if(!focusChanged.isFocused()){
			shutdown();
		}
	}

	@Subscribe
	void onWidgetLoaded(WidgetLoaded widgetLoaded){
		//System.out.println("WIDGET OP: "+widgetLoaded.getGroupId());
		if(exemptWidgets.contains(widgetLoaded.getGroupId())) return;
		openWidgets.add(widgetLoaded.getGroupId());
		forceMap = true;
	}

	@Subscribe
	void onWidgetClosed(WidgetClosed widgetClosed){
		/*System.out.println("WIDGET CL: "+widgetClosed.getGroupId());
		for(Integer widget: openWidgets) {
			System.out.println("WIDGET: " + widget);
		}*/
		if(exemptWidgets.contains(widgetClosed.getGroupId())) return;
		openWidgets.remove(widgetClosed.getGroupId());
		if(openWidgets.isEmpty()) forceMap = false;
	}
	private void drawHand(Matrix4f viewMatrix, Matrix4f handMatrix, Matrix4f cursorMatrix, Matrix4f projectionMatrix, HandSelectState state)
	{
		GL43C.glEnable(GL43C.GL_BLEND);
		// Use the texture bound in the first pass
		GL43C.glUseProgram(glHandProgram);
		GL43C.glUniformMatrix4fv(uniHandView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniCursor, false, cursorMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniHandProjection, false, projectionMatrix.get(mvpMatrix));

		if(hovering){
			GL43C.glUniform4f(uniHandColor, 255.0f, 255.0f, 255.0f, 0.75f);
		} else {
			switch (state) {
				case IDLE:
					GL43C.glUniform4f(uniHandColor, 255.0f, 255.0f, 255.0f, 0.25f);
					break;
				case HOVERING:
					break;
				case SELECTING:
					GL43C.glUniform4f(uniHandColor, 00.0f, 00.0f, 255.0f, 0.25f);
					break;
				case OUT_OF_BOUNDS:
					GL43C.glUniform4f(uniHandColor, 255.0f, 0.0f, 0.0f, 0.25f);
					break;
			}
		}

		GL43C.glUniformMatrix4fv(uniCursor, false, handMatrix.get(mvpMatrix));
		GL43C.glBindVertexArray(vaoHandHandle);
		GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 12, 3);

		GL43C.glBindVertexArray(0);
		GL43C.glUseProgram(0);
		GL43C.glDisable(GL43C.GL_BLEND);
		//System.out.println(canvas.getLocationOnScreen());
	}

	//TODO: Move this inside the XR rendering.
	private void drawUi(final int overlayColor, final int canvasHeight, final int canvasWidth, Matrix4f viewMatrix, Matrix4f projectionMatrix, Matrix4f mapMatrix)
	{
		GL43C.glEnable(GL43C.GL_BLEND);
		GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, interfaceTexture);

		// Use the texture bound in the first pass
		final UIScalingMode2 uiScalingMode = config.uiScalingMode();
		GL43C.glUseProgram(glUiProgram);
		GL43C.glUniformMatrix4fv(uniUiView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniUiProjection, false, projectionMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniUiMap, false, mapMatrix.get(mvpMatrix));
		GL43C.glUniform1i(uniTex, 0);
		GL43C.glUniform2i(uniTexSourceDimensions, canvasWidth, canvasHeight);
		GL43C.glUniform1i(uniUiColorblindIntensity, config.colorBlindIntensity());
		GL43C.glUniform4f(uniUiAlphaOverlay,
				(overlayColor >> 16 & 0xFF) / 255f,
				(overlayColor >> 8 & 0xFF) / 255f,
				(overlayColor & 0xFF) / 255f,
				(overlayColor >>> 24) / 255f
		);

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			//glDpiAwareViewport(0, 0, dim.width, dim.height);
			GL43C.glUniform2i(uniTexTargetDimensions, dim.width, dim.height);
		}
		else
		{
			//glDpiAwareViewport(0, 0, canvasWidth, canvasHeight);
			GL43C.glUniform2i(uniTexTargetDimensions, canvasWidth, canvasHeight);
		}

		// Set the sampling function used when stretching the UI.
		// This is probably better done with sampler objects instead of texture parameters, but this is easier and likely more portable.
		// See https://www.khronos.org/opengl/wiki/Sampler_Object for details.
		if (client.isStretchedEnabled())
		{
			// GL_NEAREST makes sampling for bicubic/xBR simpler, so it should be used whenever linear isn't
			final int function = GL43C.GL_NEAREST;
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MIN_FILTER, function);
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MAG_FILTER, function);
		}

		// Texture on UI
		GL43C.glBindVertexArray(vaoUiHandle);
		GL43C.glDrawArrays(GL43C.GL_TRIANGLE_FAN, 0, 4);

		// Reset
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
		GL43C.glBindVertexArray(0);
		GL43C.glUseProgram(0);
		GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);
		GL43C.glDisable(GL43C.GL_BLEND);
	}

	//TODO: Move this inside the XR rendering.
	private void drawMenu(final int overlayColor, final int menuHeight, final int menuWidth, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2, Matrix4f mapMatrix)
	{
		GL43C.glEnable(GL43C.GL_BLEND);
		GL43C.glBlendFunc(GL43C.GL_ONE, GL43C.GL_ONE_MINUS_SRC_ALPHA);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, menuTexture);

		// Use the texture bound in the first pass
		final UIScalingMode2 uiScalingMode = config.uiScalingMode();
		GL43C.glUseProgram(glMenuProgram);
		GL43C.glUniformMatrix4fv(uniMenuView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniMenuProjection, false, projectionMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniMenuMap, false, mapMatrix.get(mvpMatrix));
		GL43C.glUniform1i(uniMenuTex, 0);
		GL43C.glUniform2i(uniMenuTexSourceDimensions, menuWidth, menuHeight);
		GL43C.glUniform1i(uniMenuColorblindIntensity, config.colorBlindIntensity());
		GL43C.glUniform4f(uniMenuAlphaOverlay,
				(overlayColor >> 16 & 0xFF) / 255f,
				(overlayColor >> 8 & 0xFF) / 255f,
				(overlayColor & 0xFF) / 255f,
				(overlayColor >>> 24) / 255f
		);

		GL43C.glUniform2i(uniMenuTexTargetDimensions, menuWidth, menuHeight);

		// Set the sampling function used when stretching the UI.
		// This is probably better done with sampler objects instead of texture parameters, but this is easier and likely more portable.
		// See https://www.khronos.org/opengl/wiki/Sampler_Object for details.
		if (client.isStretchedEnabled())
		{
			// GL_NEAREST makes sampling for bicubic/xBR simpler, so it should be used whenever linear isn't
			final int function = GL43C.GL_NEAREST;
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MIN_FILTER, function);
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MAG_FILTER, function);
		}

		// Texture on UI

		float[] real;
		if(menuTileX!=null && menuTileY!=null) {
			real = new float[]{menuTileX << Perspective.LOCAL_COORD_BITS, 0, menuTileY << Perspective.LOCAL_COORD_BITS, 1.0f};
			GL43C.glUniformMatrix4fv(uniMenuProjection2, false, projectionMatrix2);
		}
		else {
			real = new float[]{0, 0, -1.0f, 1.0f};
			GL43C.glUniformMatrix4fv(uniMenuProjection2, false, new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1});
		}
		GL43C.glUniform4fv(uniMenuLoc, real);

		//GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
		// Texture on UI
		GL43C.glBindVertexArray(vaoMenuHandle);
		float scale = 0.002f;

		float ypos = menuIntersect.y/1.0f;
		float zpos = 0.0f;
		float xpos = menuIntersect.x/1.0f;

		float w = menuHeight*scale;
		float h = menuWidth*scale;
		float tiles = menuActor==null?0:((((menuActor.getWorldArea().getWidth()-1)/2)+
				((menuActor.getWorldArea().getHeight()-1)/2))/2.0f);
		//System.out.println(cha+" "+xpos+" "+ypos+" "+w+" "+h);
		// update VBO for each character
		float[] vertices = new float[]{
				xpos,    ypos + h,   zpos+0.044f+0.088f*tiles, 0.0f, 0.0f ,
				xpos,     ypos,      zpos+0.044f+0.088f*tiles,0.0f, 1.0f ,
				xpos+w, ypos,      zpos+0.044f+0.088f*tiles,1.0f, 1.0f ,

				xpos,     ypos + h,  zpos+0.044f+0.088f*tiles,0.0f, 0.0f ,
				xpos+w, ypos,      zpos+0.044f+0.088f*tiles,1.0f, 1.0f ,
				xpos+w, ypos + h,  zpos+0.044f+0.088f*tiles,1.0f, 0.0f };
		// update content of VBO memory
		glBindBuffer(GL_ARRAY_BUFFER, vboMenuHandle);
		glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		// render quad
		glDrawArrays(GL_TRIANGLES, 0, 6);
		// now advance cursors for next glyph (note that advance is number of 1/64 pixels)

		// Reset
		GL43C.glBindVertexArray(0);
		GL43C.glUseProgram(0);

		// Reset
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
		GL43C.glBindVertexArray(0);
		GL43C.glUseProgram(0);
	}

	/**
	 * Convert the front framebuffer to an Image
	 *
	 * @return
	 */
	private Image screenshot()
	{
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			width = dim.width;
			height = dim.height;
		}

		if (OSType.getOSType() != OSType.MacOS)
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			width = getScaledValue(t.getScaleX(), width);
			height = getScaledValue(t.getScaleY(), height);
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
				.order(ByteOrder.nativeOrder());

		final BufferProvider bufferProvider = client.getBufferProvider();
		final int[] pixels = bufferProvider.getPixels();
		GL43C.glReadPixels(0, 0, width, height, GL43C.GL_RGBA, GL43C.GL_UNSIGNED_BYTE, buffer);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int r = buffer.get() & 0xff;
				int g = buffer.get() & 0xff;
				int b = buffer.get() & 0xff;
				buffer.get(); // alpha

				pixels[(height - y - 1) * width + x] = (r << 16) | (g << 8) | b;
			}
		}

		return image;
	}

	@Override
	public void animate(Texture texture, int diff)
	{
		// texture animation happens on gpu
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		hudHelper.addHitsplat(hitsplatApplied);
		hudHelper.addHealthbarTimeout(hitsplatApplied.getActor(), hitsplatApplied.getHitsplat().getDisappearsOnGameCycle()+100);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		//hudHelper.updateLocations();
		hudHelper.cullHealthbars(client.getGameCycle());
		hudHelper.cullHitsplats(client.getGameCycle());

		if (client.getTickCount() > clickTick && client.getLocalDestinationLocation() == null)
		{
			// when the destination is reached, clear the interacting object
			interactedObject = null;
			interactedNpc = null;
		}
	}

	Integer menuTileX = null;
	Integer menuTileY = null;
	Actor menuActor = null;

	Integer hintTileX = null;
	Integer hintTileY = null;
	Actor hintActor = null;
	String hintAction = null;
	String hintTarget = null;

	Vector3f menuIntersect = new Vector3f(0.0f, 0.0f, 0.0f);

	Vector3f hintIntersect = new Vector3f(0.0f, 0.0f, 0.0f);

	public boolean isTargetingWorld(){
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0)
		{
			return false;
		} else {
			MenuEntry entry = menuEntries[menuEntries.length - 1];
			MenuAction menuAction = entry.getType();

			switch (menuAction) {
				case WIDGET_TARGET_ON_GAME_OBJECT:
				case GAME_OBJECT_FIRST_OPTION:
				case GAME_OBJECT_SECOND_OPTION:
				case GAME_OBJECT_THIRD_OPTION:
				case GAME_OBJECT_FOURTH_OPTION:
				case GAME_OBJECT_FIFTH_OPTION:
				case EXAMINE_OBJECT:
				case EXAMINE_ITEM_GROUND:
				case GROUND_ITEM_FIRST_OPTION:
				case GROUND_ITEM_SECOND_OPTION:
				case GROUND_ITEM_THIRD_OPTION:
				case GROUND_ITEM_FOURTH_OPTION:
				case GROUND_ITEM_FIFTH_OPTION:
				case WALK:
				case WIDGET_TARGET_ON_NPC:
				case NPC_FIRST_OPTION:
				case NPC_SECOND_OPTION:
				case NPC_THIRD_OPTION:
				case NPC_FOURTH_OPTION:
				case NPC_FIFTH_OPTION:
				case EXAMINE_NPC:
				case PLAYER_FIRST_OPTION:
				case PLAYER_SECOND_OPTION:
				case PLAYER_THIRD_OPTION:
				case PLAYER_FOURTH_OPTION:
				case PLAYER_FIFTH_OPTION:
				case PLAYER_SIXTH_OPTION:
				case PLAYER_SEVENTH_OPTION:
				case PLAYER_EIGHTH_OPTION:
					return true;
				default:
					return false;
			}
		}
	}

	public void generateHint()
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0)
		{
			hintActor = null;
			hintTileX = null;
			hintTileY = null;
			hintTarget = null;
			hintAction = null;
		} else {
			MenuEntry entry = menuEntries[menuEntries.length - 1];
			MenuAction menuAction = entry.getType();

			switch (menuAction) {
				case WIDGET_TARGET_ON_GAME_OBJECT:
				case GROUND_ITEM_FIRST_OPTION:
				case GROUND_ITEM_SECOND_OPTION:
				case GROUND_ITEM_THIRD_OPTION:
				case GROUND_ITEM_FOURTH_OPTION:
				case GROUND_ITEM_FIFTH_OPTION:
				case GAME_OBJECT_FIRST_OPTION:
				case GAME_OBJECT_SECOND_OPTION:
				case GAME_OBJECT_THIRD_OPTION:
				case GAME_OBJECT_FOURTH_OPTION:
				case GAME_OBJECT_FIFTH_OPTION: {
					hintActor = null;
					hintTileX = entry.getParam0();
					hintTileY = entry.getParam1();
					hintTarget = entry.getTarget();
					hintAction = entry.getOption();
					//System.out.println("GROUND:"+menuTileX+" "+menuTileY);
					break;
				}
				case WIDGET_TARGET_ON_NPC:
				case NPC_FIRST_OPTION:
				case NPC_SECOND_OPTION:
				case NPC_THIRD_OPTION:
				case NPC_FOURTH_OPTION:
				case NPC_FIFTH_OPTION: {
					hintActor = entry.getActor();
					if(hintActor != null) {
						hintTileX = hintActor.getLocalLocation().getSceneX();
						hintTileY = hintActor.getLocalLocation().getSceneY();
						hintTarget = entry.getTarget();
						hintAction = entry.getOption();
					} else {
						hintTileX = null;
						hintTileY = null;
						hintTarget = null;
						hintAction = null;
					}
					//System.out.println("ACTOR:"+menuTileX+" "+menuTileY);
					break;
				}
				case EXAMINE_NPC:
				case PLAYER_FIRST_OPTION:
				case PLAYER_SECOND_OPTION:
				case PLAYER_THIRD_OPTION:
				case PLAYER_FOURTH_OPTION:
				case PLAYER_FIFTH_OPTION:
				case PLAYER_SIXTH_OPTION:
				case PLAYER_SEVENTH_OPTION:
				case PLAYER_EIGHTH_OPTION:
				case EXAMINE_ITEM_GROUND:
				case WALK:
				default:
					hintActor = null;
					hintTileX = null;
					hintTileY = null;
					hintTarget = null;
					hintAction = null;
			}
		}
		if(rightPose != null) {
			hintIntersect = CalcHelper.getPlayAreaIntersect(rightPose.position$(), rightPose.orientation());
		} else {
			hintIntersect = new Vector3f(0.0f, 0.0f, 0.0f);
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menoOpened)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0)
		{
			menuActor = null;
			menuTileX = null;
			menuTileY = null;
		} else {
			MenuEntry entry = menuEntries[menuEntries.length - 1];
			MenuAction menuAction = entry.getType();

			switch (menuAction) {
				case WIDGET_TARGET_ON_GAME_OBJECT:
				case GAME_OBJECT_FIRST_OPTION:
				case GAME_OBJECT_SECOND_OPTION:
				case GAME_OBJECT_THIRD_OPTION:
				case GAME_OBJECT_FOURTH_OPTION:
				case GAME_OBJECT_FIFTH_OPTION:
				case EXAMINE_OBJECT: {
					menuActor = null;
					menuTileX = entry.getParam0();
					menuTileY = entry.getParam1();
					//System.out.println("GROUND:"+menuTileX+" "+menuTileY);
					break;
				}
				case EXAMINE_ITEM_GROUND:
				case GROUND_ITEM_FIRST_OPTION:
				case GROUND_ITEM_SECOND_OPTION:
				case GROUND_ITEM_THIRD_OPTION:
				case GROUND_ITEM_FOURTH_OPTION:
				case GROUND_ITEM_FIFTH_OPTION:
				case WALK: {
					menuActor = null;
					Tile tile = client.getSelectedSceneTile();
					if(tile != null) {
						menuTileX = tile.getLocalLocation().getSceneX();
						menuTileY = tile.getLocalLocation().getSceneY();
					} else {
						menuTileX = null;
						menuTileY = null;
					}
					//System.out.println("GROUND:"+menuTileX+" "+menuTileY);
					break;
				}
				case WIDGET_TARGET_ON_NPC:
				case NPC_FIRST_OPTION:
				case NPC_SECOND_OPTION:
				case NPC_THIRD_OPTION:
				case NPC_FOURTH_OPTION:
				case NPC_FIFTH_OPTION:
				case EXAMINE_NPC:
				case PLAYER_FIRST_OPTION:
				case PLAYER_SECOND_OPTION:
				case PLAYER_THIRD_OPTION:
				case PLAYER_FOURTH_OPTION:
				case PLAYER_FIFTH_OPTION:
				case PLAYER_SIXTH_OPTION:
				case PLAYER_SEVENTH_OPTION:
				case PLAYER_EIGHTH_OPTION: {
					menuActor = entry.getActor();
					menuTileX = menuActor.getLocalLocation().getSceneX();
					menuTileY = menuActor.getLocalLocation().getSceneY();
					//System.out.println("ACTOR:"+menuTileX+" "+menuTileY);
					break;
				}
				default:
					menuActor = null;
					menuTileX = null;
					menuTileY = null;
			}
		}
		if(rightPose != null) {
			menuIntersect = CalcHelper.getPlayAreaIntersect(rightPose.position$(), rightPose.orientation());
		} else {
			menuIntersect = new Vector3f(0.0f, 0.0f, 0.0f);
		}
		/*int curY = client.getMenuY()+19;
		for(int i = client.getMenuEntries().length-1; i>=0 ;i--){
			curY+=15;
			if(curY > lastCanvasHeight){
				client.createMenuEntry(i+1).setOption("Cancel")
				.setTarget("").setType(MenuAction.CANCEL);
			}
		}*/
		if(client.getMenuY()+19+(15*client.getMenuEntries().length) > lastCanvasHeight){
			client.createMenuEntry(-1).setOption("Cancel")
					.setTarget("").setType(MenuAction.CANCEL);
		}
	}

	private void drawUi(final int overlayColor, final int canvasHeight, final int canvasWidth)
	{
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);

		// Use the texture bound in the first pass
		final UIScalingMode2 uiScalingMode = config.uiScalingMode();
		glUseProgram(glUiProgram);
		glUniform1i(uniTex, 0);
		glUniform2i(uniTexSourceDimensions, canvasWidth, canvasHeight);
		glUniform4f(uniUiAlphaOverlay,
			(overlayColor >> 16 & 0xFF) / 255f,
			(overlayColor >> 8 & 0xFF) / 255f,
			(overlayColor & 0xFF) / 255f,
			(overlayColor >>> 24) / 255f
		);
		glUniform1f(uniUiColorblindIntensity, config.colorBlindIntensity());

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			glDpiAwareViewport(0, 0, dim.width, dim.height);
			glUniform2i(uniTexTargetDimensions, dim.width, dim.height);
		}
		else
		{
			glDpiAwareViewport(0, 0, canvasWidth, canvasHeight);
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			glUniform2i(uniTexTargetDimensions, getScaledValue(t.getScaleX(), canvasWidth), getScaledValue(t.getScaleY(), canvasHeight));
		}

		// Set the sampling function used when stretching the UI.
		// This is probably better done with sampler objects instead of texture parameters, but this is easier and likely more portable.
		// See https://www.khronos.org/opengl/wiki/Sampler_Object for details.
		// GL_NEAREST makes sampling for bicubic/xBR simpler, so it should be used whenever linear/hybrid isn't
		final int function = uiScalingMode == UIScalingMode2.LINEAR || uiScalingMode == UIScalingMode2.HYBRID ? GL_LINEAR : GL_NEAREST;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, function);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, function);

		// Texture on UI
		glBindVertexArray(vaoUiHandle);
		glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

		// Reset
		glBindTexture(GL_TEXTURE_2D, 0);
		glBindVertexArray(0);
		glUseProgram(0);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_BLEND);
	}

	/**
	 * Convert the front framebuffer to an Image
	 *
	 * @return
	 */
	/*private Image screenshot()
	{
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			width = dim.width;
			height = dim.height;
		}

		final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
		final AffineTransform t = graphicsConfiguration.getDefaultTransform();
		width = getScaledValue(t.getScaleX(), width);
		height = getScaledValue(t.getScaleY(), height);

		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
			.order(ByteOrder.nativeOrder());

		glReadBuffer(awtContext.getBufferMode());
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int r = buffer.get() & 0xff;
				int g = buffer.get() & 0xff;
				int b = buffer.get() & 0xff;
				buffer.get(); // alpha

				pixels[(height - y - 1) * width + x] = (r << 16) | (g << 8) | b;
			}
		}

		return image;
	}*/

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();
		if (state.getState() < GameState.LOADING.getState())
		{
			// this is to avoid scene fbo blit when going from <loading to >=loading,
			// but keep it when doing >loading to loading
			sceneFboValid = false;
		}
		if (state == GameState.STARTING)
		{
			if (textureArrayId != -1)
			{
				textureManager.freeTextureArray(textureArrayId);
			}
			textureArrayId = -1;
			lastAnisotropicFilteringLevel = -1;
		}

		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
			targetOutlineBufferOffset = 0;
		}
		if (gameStateChanged.getGameState() == GameState.STARTING)
		{
			if (textureArrayId != -1)
			{
				textureManager.freeTextureArray(textureArrayId);
			}
			textureArrayId = -1;
			lastAnisotropicFilteringLevel = -1;
		}
		if (gameStateChanged.getGameState() == GameState.LOADING)
		{
			interactedObject = null;
		}
	}

	@Override
	public void loadScene(WorldView worldView, Scene scene)
	{
		if (scene.getWorldViewId() != WorldView.TOPLEVEL)
		{
			loadSubScene(worldView, scene);
			return;
		}

		if (nextZones != null)
		{
			log.debug("Double zone load!");
			// The previous scene load just gets dropped, this is uncommon and requires a back to back map build packet
			// while having the first load take more than a full server cycle to complete
			CountDownLatch latch = new CountDownLatch(1);
			clientThread.invoke(() ->
			{
				for (int x = 0; x < NUM_ZONES; ++x)
				{
					for (int z = 0; z < NUM_ZONES; ++z)
					{
						Zone zone = nextZones[x][z];
						assert !zone.cull;
						// anything initialized is a reused zone and so shouldn't be freed
						if (!zone.initialized)
						{
							zone.unmap();
							zone.initialized = true;
							zone.free();
						}
					}
				}
				latch.countDown();
			});
			try
			{
				latch.await();
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
			nextZones = null;
			nextRoofChanges = null;
		}

		SceneContext ctx = root;
		Scene prev = client.getTopLevelWorldView().getScene();

		regionManager.prepare(scene);

		int dx = scene.getBaseX() - prev.getBaseX() >> 3;
		int dy = scene.getBaseY() - prev.getBaseY() >> 3;

		final int SCENE_ZONES = NUM_ZONES;

		// initially mark every zone as needing culled
		for (int x = 0; x < SCENE_ZONES; ++x)
		{
			for (int z = 0; z < SCENE_ZONES; ++z)
			{
				ctx.zones[x][z].cull = true;
			}
		}

		Map<Integer, Integer> roofChanges = new HashMap<>();

		// find zones which overlap and copy them
		Zone[][] newZones = new Zone[SCENE_ZONES][SCENE_ZONES];
		final GameState gameState = client.getGameState();
		if (prev.isInstance() == scene.isInstance()
			&& gameState == GameState.LOGGED_IN)
		{
			int[][][] prevTemplates = prev.getInstanceTemplateChunks();
			int[][][] curTemplates = scene.getInstanceTemplateChunks();

			int[][][] prids = prev.getRoofs();
			int[][][] nrids = scene.getRoofs();

			for (int x = 0; x < SCENE_ZONES; ++x)
			{
				next:
				for (int z = 0; z < SCENE_ZONES; ++z)
				{
					int ox = x + dx;
					int oz = z + dy;

					// Reused the old zone if it is also in the new scene, except for the edges, to work around
					// tile blending, (edge) shadows, sharelight, etc.
					if (canReuse(ctx.zones, ox, oz))
					{
						if (scene.isInstance())
						{
							// Convert from modified chunk coordinates to Jagex chunk coordinates
							int jx = x - (SCENE_OFFSET / 8);
							int jz = z - (SCENE_OFFSET / 8);
							int jox = ox - (SCENE_OFFSET / 8);
							int joz = oz - (SCENE_OFFSET / 8);
							// Check Jagex chunk coordinates are within the Jagex scene
							if (jx >= 0 && jx < Constants.SCENE_SIZE / 8 && jz >= 0 && jz < Constants.SCENE_SIZE / 8)
							{
								if (jox >= 0 && jox < Constants.SCENE_SIZE / 8 && joz >= 0 && joz < Constants.SCENE_SIZE / 8)
								{
									for (int level = 0; level < 4; ++level)
									{
										int prevTemplate = prevTemplates[level][jox][joz];
										int curTemplate = curTemplates[level][jx][jz];
										if (prevTemplate != curTemplate)
										{
											log.error("Instance template reuse mismatch! prev={} cur={}", prevTemplate, curTemplate);
											continue next;
										}
									}
								}
							}
						}

						Zone old = ctx.zones[ox][oz];
						assert old.initialized;

						if (old.dirty)
						{
							continue;
						}

						assert old.sizeO > 0 || old.sizeA > 0;

						// Roof ids aren't consistent between scenes, so build a mapping of old -> new roof ids
						// Sometimes groups split or merge, so we can't copy the zone in that case
						for (int level = 0; level < 4; level++)
						{
							for (int tx = 0; tx < 8; tx++)
							{
								for (int tz = 0; tz < 8; tz++)
								{
									int prid = prids[level][(ox << 3) + tx][(oz << 3) + tz];
									int nrid = nrids[level][(x << 3) + tx][(z << 3) + tz];

									if (prid != nrid && (prid == 0 || nrid == 0))
									{
										log.trace("Roof mismatch: {} -> {}", prid, nrid);
										continue next;
									}

									Integer orid = roofChanges.putIfAbsent(prid, nrid);
									if (orid == null)
									{
										log.trace("Roof change: {} -> {}", prid, nrid);
									}
									else if (orid != nrid)
									{
										log.trace("Roof mismatch: {} -> {} vs {}", prid, nrid, orid);
										continue next;
									}
								}
							}
						}

						assert old.cull;
						old.cull = false;

						newZones[x][z] = old;
					}
				}
			}
		}

		// Fill out any zones that weren't copied
		for (int x = 0; x < SCENE_ZONES; ++x)
		{
			for (int z = 0; z < SCENE_ZONES; ++z)
			{
				if (newZones[x][z] == null)
				{
					newZones[x][z] = new Zone();
				}
			}
		}

		// size the zones which require upload
		Stopwatch sw = Stopwatch.createStarted();
		int len = 0, lena = 0;
		int reused = 0, newzones = 0;
		for (int x = 0; x < NUM_ZONES; ++x)
		{
			for (int z = 0; z < NUM_ZONES; ++z)
			{
				Zone zone = newZones[x][z];
				if (!zone.initialized)
				{
					assert zone.glVao == 0;
					assert zone.glVaoA == 0;
					mapUploader.zoneSize(scene, zone, x, z);
					len += zone.sizeO;
					lena += zone.sizeA;
					newzones++;
				}
				else
				{
					reused++;
				}
			}
		}
		log.debug("Scene size time {} reused {} new {} len opaque {} size opaque {}kb len alpha {} size alpha {}kb",
			sw, reused, newzones,
			len, (len * Zone.VERT_SIZE * 3) / 1024,
			lena, (lena * Zone.VERT_SIZE * 3) / 1024);

		// allocate buffers for zones which require upload
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invoke(() ->
		{
			for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE >> 3; ++x)
			{
				for (int z = 0; z < Constants.EXTENDED_SCENE_SIZE >> 3; ++z)
				{
					Zone zone = newZones[x][z];

					if (zone.initialized)
					{
						continue;
					}

					VBO o = null, a = null;
					int sz = zone.sizeO * Zone.VERT_SIZE * 3;
					if (sz > 0)
					{
						o = new VBO(sz);
						o.init(GL_STATIC_DRAW);
						o.map();
					}

					sz = zone.sizeA * Zone.VERT_SIZE * 3;
					if (sz > 0)
					{
						a = new VBO(sz);
						a.init(GL_STATIC_DRAW);
						a.map();
					}

					zone.init(o, a);
				}
			}

			latch.countDown();
		});
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}

		// upload zones
		sw = Stopwatch.createStarted();
		for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE >> 3; ++x)
		{
			for (int z = 0; z < Constants.EXTENDED_SCENE_SIZE >> 3; ++z)
			{
				Zone zone = newZones[x][z];

				if (!zone.initialized)
				{
					mapUploader.uploadZone(scene, zone, x, z);
				}
			}
		}
		log.debug("Scene upload time {}", sw);

		nextZones = newZones;
		nextRoofChanges = roofChanges;
	}

	private static boolean canReuse(Zone[][] zones, int zx, int zz)
	{
		// For tile blending, sharelight, and shadows to work correctly, the zones surrounding
		// the zone must be valid.
		for (int x = zx - 1; x <= zx + 1; ++x)
		{
			if (x < 0 || x >= NUM_ZONES)
			{
				return false;
			}
			for (int z = zz - 1; z <= zz + 1; ++z)
			{
				if (z < 0 || z >= NUM_ZONES)
				{
					return false;
				}
				Zone zone = zones[x][z];
				if (!zone.initialized)
				{
					return false;
				}
				if (zone.sizeO == 0 && zone.sizeA == 0)
				{
					return false;
				}
			}
		}
		return true;
	}

	private void loadSubScene(WorldView worldView, Scene scene)
	{
		int worldViewId = scene.getWorldViewId();
		assert worldViewId != -1;

		log.debug("Loading world view {}", worldViewId);

		SceneContext ctx0 = subs[worldViewId];
		if (ctx0 != null)
		{
			log.info("Reload of an already loaded worldview?");
			return;
		}

		final SceneContext ctx = new SceneContext(worldView.getSizeX() >> 3, worldView.getSizeY() >> 3);
		subs[worldViewId] = ctx;

		for (int x = 0; x < ctx.sizeX; ++x)
		{
			for (int z = 0; z < ctx.sizeZ; ++z)
			{
				Zone zone = ctx.zones[x][z];
				mapUploader.zoneSize(scene, zone, x, z);
			}
		}

		// allocate buffers for zones which require upload
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invoke(() ->
		{
			for (int x = 0; x < ctx.sizeX; ++x)
			{
				for (int z = 0; z < ctx.sizeZ; ++z)
				{
					Zone zone = ctx.zones[x][z];

					VBO o = null, a = null;
					int sz = zone.sizeO * Zone.VERT_SIZE * 3;
					if (sz > 0)
					{
						o = new VBO(sz);
						o.init(GL_STATIC_DRAW);
						o.map();
					}

					sz = zone.sizeA * Zone.VERT_SIZE * 3;
					if (sz > 0)
					{
						a = new VBO(sz);
						a.init(GL_STATIC_DRAW);
						a.map();
					}

					zone.init(o, a);
				}
			}

			latch.countDown();
		});
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}

		for (int x = 0; x < ctx.sizeX; ++x)
		{
			for (int z = 0; z < ctx.sizeZ; ++z)
			{
				Zone zone = ctx.zones[x][z];

				mapUploader.uploadZone(scene, zone, x, z);
			}
		}
	}

	@Override
	public void despawnWorldView(WorldView worldView)
	{
		int worldViewId = worldView.getId();
		if (worldViewId != WorldView.TOPLEVEL)
		{
			log.debug("WorldView despawn: {}", worldViewId);
			var sub = subs[worldViewId];
			if (sub == null)
			{
				return;
			}

			sub.free();
			subs[worldViewId] = null;
		}
	}

	@Override
	public void swapScene(Scene scene)
	{
		if (scene.getWorldViewId() != WorldView.TOPLEVEL)
		{
			swapSub(scene);
			return;
		}

		SceneContext ctx = root;
		for (int x = 0; x < ctx.sizeX; ++x)
		{
			for (int z = 0; z < ctx.sizeZ; ++z)
			{
				Zone zone = ctx.zones[x][z];

				if (zone.cull)
				{
					zone.free();
				}
				else
				{
					// reused zone
					zone.updateRoofs(nextRoofChanges);
				}
			}
		}
		nextRoofChanges = null;

		ctx.zones = nextZones;
		nextZones = null;

		// setup vaos
		for (int x = 0; x < ctx.zones.length; ++x) // NOPMD: ForLoopCanBeForeach
		{
			for (int z = 0; z < ctx.zones[0].length; ++z)
			{
				Zone zone = ctx.zones[x][z];

				if (!zone.initialized)
				{
					zone.unmap();
					zone.initialized = true;
				}
			}
		}

		checkGLErrors();
	}

	private void swapSub(Scene scene)
	{
		SceneContext ctx = context(scene);
		if (ctx == null)
		{
			return;
		}

		// setup vaos
		for (int x = 0; x < ctx.sizeX; ++x)
		{
			for (int z = 0; z < ctx.sizeZ; ++z)
			{
				Zone zone = ctx.zones[x][z];

				if (!zone.initialized)
				{
					zone.unmap();
					zone.initialized = true;
				}
			}
		}
		log.debug("WorldView ready: {}", scene.getWorldViewId());
	}

	private int getScaledValue(final double scale, final int value)
	{
		return (int) (value * scale);
	}

	private void glDpiAwareViewport(final int x, final int y, final int width, final int height)
	{
		final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
		final AffineTransform t = graphicsConfiguration.getDefaultTransform();
		glViewport(
			getScaledValue(t.getScaleX(), x),
			getScaledValue(t.getScaleY(), y),
			getScaledValue(t.getScaleX(), width),
			getScaledValue(t.getScaleY(), height));
	}

	private int getDrawDistance()
	{
		return Ints.constrainToRange(config.drawDistance(), 0, MAX_DISTANCE);
	}

	private void updateBuffer(@Nonnull com.vr.GLBuffer glBuffer, int target, @Nonnull IntBuffer data, int usage, long clFlags)
	{
		int size = data.remaining() << 2;
		updateBuffer(glBuffer, target, size, usage, clFlags);
		GL43C.glBufferSubData(target, 0, data);
	}

	private void updateBuffer(@Nonnull com.vr.GLBuffer glBuffer, int target, @Nonnull FloatBuffer data, int usage, long clFlags)
	{
		int size = data.remaining() << 2;
		updateBuffer(glBuffer, target, size, usage, clFlags);
		GL43C.glBufferSubData(target, 0, data);
	}

	private void updateBuffer(@Nonnull com.vr.GLBuffer glBuffer, int target, int size, int usage, long clFlags)
	{
		GL43C.glBindBuffer(target, glBuffer.glBufferId);
		if (glCapabilities.glInvalidateBufferData != 0L)
		{
			// https://www.khronos.org/opengl/wiki/Buffer_Object_Streaming suggests buffer re-specification is useful
			// to avoid implicit synching. We always need to trash the whole buffer anyway so this can't hurt.
			GL43C.glInvalidateBufferData(glBuffer.glBufferId);
		}
		if (size > glBuffer.size)
		{
			int newSize = Math.max(1024, nextPowerOfTwo(size));
			log.trace("Buffer resize: {} {} -> {}", glBuffer.name, glBuffer.size, newSize);

			glBuffer.size = newSize;
			GL43C.glBufferData(target, newSize, usage);
			//recreateCLBuffer(glBuffer, clFlags);
		}
	}

	private static int nextPowerOfTwo(int v)
	{
		v--;
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v++;
		return v;
	}

	private void checkGLErrors()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}

		for (; ; )
		{
			int err = glGetError();
			if (err == GL_NO_ERROR)
			{
				return;
			}

			String errStr;
			switch (err)
			{
				case GL_INVALID_ENUM:
					errStr = "INVALID_ENUM";
					break;
				case GL_INVALID_VALUE:
					errStr = "INVALID_VALUE";
					break;
				case GL_INVALID_OPERATION:
					errStr = "INVALID_OPERATION";
					break;
				case GL_INVALID_FRAMEBUFFER_OPERATION:
					errStr = "INVALID_FRAMEBUFFER_OPERATION";
					break;
				default:
					errStr = "" + err;
					break;
			}

			log.debug("glGetError:", new Exception(errStr));
		}
	}
}
