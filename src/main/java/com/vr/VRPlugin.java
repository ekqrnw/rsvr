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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import com.vr.config.AntiAliasingMode;
import com.vr.config.UIScalingMode;
import com.vr.template.Template;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.OSType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL10GL;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opengl.*;
import org.lwjgl.openxr.*;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.nio.*;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT32;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.openxr.EXTDebugUtils.*;
import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.openxr.MNDXEGLEnable.XR_MNDX_EGL_ENABLE_EXTENSION_NAME;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.openxr.XR10.xrCreateInstance;
import static org.lwjgl.system.MemoryStack.stackMalloc;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

@PluginDescriptor(
		name = "VR",
		description = "Renders to a VR headset",
		enabledByDefault = false,
		tags = {"resources/vr", "oculus"},
		loadInSafeMode = false
)
@Slf4j
public class VRPlugin extends Plugin implements DrawCallbacks
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
	long                           glColorFormat;
	XrView.Buffer                  views;       //Each view reperesents an eye in the headset with views[0] being left and views[1] being right
	Swapchain[]                    swapchains;  //One swapchain per view
	XrViewConfigurationView.Buffer viewConfigs;
	int                            viewConfigType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

	private static Matrix4f modelviewMatrix  = new Matrix4f();
	private static Matrix4f projectionMatrix = new Matrix4f();
	private static Matrix4f viewMatrix       = new Matrix4f();

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

	// This is the maximum number of triangles the compute shaders support
	static final int MAX_TRIANGLE = 6144;
	static final int SMALL_TRIANGLE_COUNT = 512;
	private static final int FLAG_SCENE_BUFFER = Integer.MIN_VALUE;
	private static final int DEFAULT_DISTANCE = 25;
	static final int MAX_DISTANCE = 184;
	static final int MAX_FOG_DEPTH = 100;
	static final int SCENE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2; // offset for sxy -> msxy
	private static final int GROUND_MIN_Y = 350; // how far below the ground models extend

	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private com.vr.OpenCLManager openCLManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private VRPluginConfig config;

	@Inject
	private com.vr.TextureManager textureManager;

	@Inject
	private com.vr.SceneUploader sceneUploader;

	@Inject
	private DrawManager drawManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private Hooks hooks;

	enum ComputeMode
	{
		NONE,
		OPENGL,
		OPENCL
	}

	private ComputeMode computeMode = ComputeMode.NONE;

	private Canvas canvas;
	//private AWTContext awtContext;
	private Callback debugCallback;

	private GLCapabilities glCapabilities;

	static final String LINUX_VERSION_HEADER =
		"#version 420\n" +
			"#extension GL_ARB_compute_shader : require\n" +
			"#extension GL_ARB_shader_storage_buffer_object : require\n" +
			"#extension GL_ARB_explicit_attrib_location : require\n";
	static final String WINDOWS_VERSION_HEADER = "#version 430\n";

	static final com.vr.Shader PROGRAM = new com.vr.Shader()
		.add(GL43C.GL_VERTEX_SHADER, "resources/vert.glsl")
		.add(GL43C.GL_GEOMETRY_SHADER, "resources/geom.glsl")
		.add(GL43C.GL_FRAGMENT_SHADER, "resources/frag.glsl");

	static final com.vr.Shader COMPUTE_PROGRAM = new com.vr.Shader()
		.add(GL43C.GL_COMPUTE_SHADER, "resources/comp.glsl");

	static final com.vr.Shader SMALL_COMPUTE_PROGRAM = new com.vr.Shader()
		.add(GL43C.GL_COMPUTE_SHADER, "resources/comp.glsl");

	static final com.vr.Shader UNORDERED_COMPUTE_PROGRAM = new com.vr.Shader()
		.add(GL43C.GL_COMPUTE_SHADER, "resources/comp_unordered.glsl");

	static final com.vr.Shader UI_PROGRAM = new Shader()
		.add(GL43C.GL_VERTEX_SHADER, "resources/vertui.glsl")
		.add(GL43C.GL_FRAGMENT_SHADER, "resources/fragui.glsl");

	private int glProgram;
	private int glComputeProgram;
	private int glSmallComputeProgram;
	private int glUnorderedComputeProgram;
	private int glUiProgram;

	private int vaoCompute;
	private int vaoTemp;

	private int interfaceTexture;
	private int interfacePbo;

	private int vaoUiHandle;
	private int vboUiHandle;

	private int fboSceneHandle;
	private int rboSceneHandle;

	private final com.vr.GLBuffer sceneVertexBuffer = new com.vr.GLBuffer("scene vertex buffer");
	private final com.vr.GLBuffer sceneUvBuffer = new com.vr.GLBuffer("scene tex buffer");
	private final com.vr.GLBuffer tmpVertexBuffer = new com.vr.GLBuffer("tmp vertex buffer");
	private final com.vr.GLBuffer tmpUvBuffer = new com.vr.GLBuffer("tmp tex buffer");
	private final com.vr.GLBuffer tmpModelBufferLarge = new com.vr.GLBuffer("model buffer large");
	private final com.vr.GLBuffer tmpModelBufferSmall = new com.vr.GLBuffer("model buffer small");
	private final com.vr.GLBuffer tmpModelBufferUnordered = new com.vr.GLBuffer("model buffer unordered");
	private final com.vr.GLBuffer tmpOutBuffer = new com.vr.GLBuffer("out vertex buffer");
	private final com.vr.GLBuffer tmpOutUvBuffer = new com.vr.GLBuffer("out tex buffer");

	private int textureArrayId;
	private int tileHeightTex;

	private final com.vr.GLBuffer uniformBuffer = new com.vr.GLBuffer("uniform buffer");

	private com.vr.GpuIntBuffer vertexBuffer;
	private com.vr.GpuFloatBuffer uvBuffer;

	private com.vr.GpuIntBuffer modelBufferUnordered;
	private com.vr.GpuIntBuffer modelBufferSmall;
	private com.vr.GpuIntBuffer modelBuffer;

	private int unorderedModels;

	/**
	 * number of models in small buffer
	 */
	private int smallModels;

	/**
	 * number of models in large buffer
	 */
	private int largeModels;

	/**
	 * offset in the target buffer for model
	 */
	private int targetBufferOffset;

	/**
	 * offset into the temporary scene vertex buffer
	 */
	private int tempOffset;

	/**
	 * offset into the temporary scene uv buffer
	 */
	private int tempUvOffset;

	private int lastCanvasWidth;
	private int lastCanvasHeight;
	private int lastStretchedCanvasWidth;
	private int lastStretchedCanvasHeight;
	private AntiAliasingMode lastAntiAliasingMode;
	private int lastAnisotropicFilteringLevel = -1;

	private double cameraX, cameraY, cameraZ;
	private double cameraYaw, cameraPitch;

	private int viewportOffsetX;
	private int viewportOffsetY;

	// Uniforms
	private int uniColorBlindMode;
	private int uniUiColorBlindMode;
	private int uniUseFog;
	private int uniFogColor;
	private int uniFogDepth;
	private int uniDrawDistance;
	private int uniExpandedMapLoadingChunks;
	private int uniProjectionMatrix;
	private int uniBrightness;
	private int uniTex;
	private int uniTexSamplingMode;
	private int uniTexSourceDimensions;
	private int uniTexTargetDimensions;
	private int uniUiAlphaOverlay;
	private int uniTextures;
	private int uniTextureAnimations;
	private int uniBlockSmall;
	private int uniBlockLarge;
	private int uniBlockMain;
	private int uniSmoothBanding;
	private int uniTextureLightMode;
	private int uniTick;

	private boolean lwjglInitted = false;

	private int sceneId;
	private int nextSceneId;
	private com.vr.GpuIntBuffer nextSceneVertexBuffer;
	private com.vr.GpuFloatBuffer nextSceneTexBuffer;

	private int uniUiProjection;

	private int uniUiView;

	private int uniProjection;

	private int uniView;

	private int uniModel;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	////private HelloOpenXRGL openXR;

	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI) {
		return ((renderable instanceof Player || renderable instanceof NPC) && !drawingUI);
	}

	public void check(int result) throws IllegalStateException {
		if (XR_SUCCEEDED(result)) {
			return;
		}

		if (xrInstance != null) {
			ByteBuffer str = stackMalloc(XR_MAX_RESULT_STRING_SIZE);
			if (xrResultToString(xrInstance, result, str) >= 0) {
				throw new XrResultException(memUTF8(str, memLengthNT1(str)));
			}
		}

		throw new XrResultException("XR method returned " + result);
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

			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, majorVersionToRequest);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minorVersionToRequest);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
			glfwWindowHint(GLFW_DOUBLEBUFFER, GL_FALSE);
			if (useEglGraphicsBinding) {
				glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
			}
			window = glfwCreateWindow(640, 480, "Hello World", NULL, NULL);
			glfwMakeContextCurrent(window);

			glCapabilities = GL.createCapabilities();

			log.info("Using device: {}", GL43C.glGetString(GL43C.GL_RENDERER));
			log.info("Using driver: {}", GL43C.glGetString(GL43C.GL_VERSION));

			if (!glCapabilities.OpenGL31)
			{
				throw new RuntimeException("OpenGL 3.1 is required but not available");
			}

			if (!glCapabilities.OpenGL43 && computeMode == ComputeMode.OPENGL)
			{
				log.info("disabling compute shaders because OpenGL 4.3 is not available");
				computeMode = ComputeMode.NONE;
			}

			if (computeMode == ComputeMode.NONE)
			{
				sceneUploader.initSortingBuffers();
			}

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

				swapchains = new Swapchain[viewCountNumber];
				for (int i = 0; i < viewCountNumber; i++) {
					XrViewConfigurationView viewConfig = viewConfigs.get(i);

					Swapchain swapchainWrapper = new Swapchain();

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
		for (Swapchain swapchain : swapchains) {
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

	private boolean pollEvents() {
		glfwPollEvents();
		XrEventDataBaseHeader event = readNextOpenXREvent();
		if (event == null) {
			return false;
		}

		do {
			switch (event.type()) {
				case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING: {
					XrEventDataInstanceLossPending instanceLossPending = XrEventDataInstanceLossPending.create(event);
					System.err.printf("XrEventDataInstanceLossPending by %d\n", instanceLossPending.lossTime());
					//*requestRestart = true;
					return true;
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

		return false;
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

	boolean OpenXRHandleSessionStateChangedEvent(XrEventDataSessionStateChanged stateChangedEvent) {
		int oldState = sessionState;
		sessionState = stateChangedEvent.state();

		System.out.printf("XrEventDataSessionStateChanged: state %s->%s session=%d time=%d\n", oldState, sessionState, stateChangedEvent.session(), stateChangedEvent.time());

		if ((stateChangedEvent.session() != NULL) && (stateChangedEvent.session() != xrSession.address())) {
			System.err.println("XrEventDataSessionStateChanged for unknown session");
			return false;
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
					return false;
				}
			}
			case XR_SESSION_STATE_STOPPING: {
				assert (xrSession != null);
				sessionRunning = false;
				log.info("ENDING.");
				check(xrEndSession(xrSession));
				shutDown();
				return false;
			}
			case XR_SESSION_STATE_EXITING: {
				// Do not attempt to restart because user closed this session.
				//*requestRestart = false;
				return true;
			}
			case XR_SESSION_STATE_LOSS_PENDING: {
				// Poll for a new instance.
				//*requestRestart = true;
				return true;
			}
			default:
				return false;
		}
	}

	@Override
	protected void startUp()
	{
		clientThread.invoke(() ->
		{
			try
			{
				hooks.registerRenderableDrawListener(drawListener);

				fboSceneHandle = rboSceneHandle = -1; // AA FBO
				targetBufferOffset = 0;
				unorderedModels = smallModels = largeModels = 0;

				//AWTContext.loadNatives();

				canvas = client.getCanvas();

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

				computeMode = config.useComputeShaders()
					? (OSType.getOSType() == OSType.MacOS ? ComputeMode.OPENCL : ComputeMode.OPENGL)
					: ComputeMode.NONE;

				// lwjgl defaults to lwjgl- + user.name, but this breaks if the username would cause an invalid path
				// to be created.
				Configuration.SHARED_LIBRARY_EXTRACT_DIRECTORY.set("lwjgl-rl");

				createOpenXRInstance();
				initializeOpenXRSystem();
				initializeAndBindOpenGL();

				lwjglInitted = true;

				checkGLErrors();
				if (log.isDebugEnabled() && glCapabilities.glDebugMessageControl != 0)
				{
					debugCallback = GLUtil.setupDebugMessageCallback();
					if (debugCallback != null)
					{
						//	GLDebugEvent[ id 0x20071
						//		type Warning: generic
						//		severity Unknown (0x826b)
						//		source GL API
						//		msg Buffer detailed info: Buffer object 11 (bound to GL_ARRAY_BUFFER_ARB, and GL_SHADER_STORAGE_BUFFER (4), usage hint is GL_STREAM_DRAW) will use VIDEO memory as the source for buffer object operations.
						GL43C.glDebugMessageControl(GL43C.GL_DEBUG_SOURCE_API, GL43C.GL_DEBUG_TYPE_OTHER,
							GL43C.GL_DONT_CARE, 0x20071, false);

						//	GLDebugMessageHandler: GLDebugEvent[ id 0x20052
						//		type Warning: implementation dependent performance
						//		severity Medium: Severe performance/deprecation/other warnings
						//		source GL API
						//		msg Pixel-path performance warning: Pixel transfer is synchronized with 3D rendering.
						GL43C.glDebugMessageControl(GL43C.GL_DEBUG_SOURCE_API, GL43C.GL_DEBUG_TYPE_PERFORMANCE,
							GL43C.GL_DONT_CARE, 0x20052, false);
					}
				}

				vertexBuffer = new com.vr.GpuIntBuffer();
				uvBuffer = new com.vr.GpuFloatBuffer();

				modelBufferUnordered = new com.vr.GpuIntBuffer();
				modelBufferSmall = new com.vr.GpuIntBuffer();
				modelBuffer = new com.vr.GpuIntBuffer();

				setupSyncMode();

				initBuffers();
				initVao();
				try
				{
					initProgram();
				}
				catch (com.vr.ShaderException ex)
				{
					throw new RuntimeException(ex);
				}
				initInterfaceTexture();
				initUniformBuffer();

				client.setDrawCallbacks(this);
				client.setGpuFlags(DrawCallbacks.GPU
					| (computeMode == ComputeMode.NONE ? 0 : DrawCallbacks.HILLSKEW)
				);
				client.setExpandedMapLoading(config.expandedMapLoadingChunks());

				// force rebuild of main buffer provider to enable alpha channel
				client.resizeCanvas();

				lastCanvasWidth = lastCanvasHeight = -1;
				lastStretchedCanvasWidth = lastStretchedCanvasHeight = -1;
				lastAntiAliasingMode = null;

				textureArrayId = -1;

				////openXR = new HelloOpenXRGL();
				////openXR.XR_BEGIN();

				if (client.getGameState() == GameState.LOGGED_IN)
				{
					Scene scene = client.getScene();
					loadScene(scene);
					swapScene(scene);
				}

				createXRReferenceSpace();
				createXRSwapchains();
				createOpenGLResourses();

				checkGLErrors();

				eventDataBuffer = XrEventDataBuffer.calloc()
						.type$Default();

				while (!pollEvents() && !glfwWindowShouldClose(window)) {
					if (sessionRunning) {
						break;
					} else {
						// Throttle loop since xrWaitFrame won't be called.
						Thread.sleep(250);
					}
				}
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

				shutDown();
			}
			return true;
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
			for (Swapchain swapchain : swapchains) {
				xrDestroySwapchain(swapchain.handle);
				swapchain.images.free();
			}

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

			sceneUploader.releaseSortingBuffers();

			if (lwjglInitted)
			{
				if (textureArrayId != -1)
				{
					textureManager.freeTextureArray(textureArrayId);
					textureArrayId = -1;
				}

				if (tileHeightTex != 0)
				{
					GL43C.glDeleteTextures(tileHeightTex);
					tileHeightTex = 0;
				}

				destroyGlBuffer(uniformBuffer);

				shutdownInterfaceTexture();
				shutdownProgram();
				shutdownVao();
				shutdownBuffers();
				////shutdownAAFbo();
			}

			// this must shutdown after the clgl buffers are freed
			openCLManager.cleanup();

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

			glCapabilities = null;

			vertexBuffer = null;
			uvBuffer = null;

			modelBufferSmall = null;
			modelBuffer = null;
			modelBufferUnordered = null;

			lastAnisotropicFilteringLevel = -1;

			// force main buffer provider rebuild to turn off alpha channel
			client.resizeCanvas();
		});
	}

	@Provides
	VRPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VRPluginConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().equals(VRPluginConfig.GROUP))
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
					client.setExpandedMapLoading(config.expandedMapLoadingChunks());
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						client.setGameState(GameState.LOADING);
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
		VRPluginConfig.SyncMode syncMode = unlockFps
			? this.config.syncMode()
			: VRPluginConfig.SyncMode.OFF;

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

	private Template createTemplate(int threadCount, int facesPerThread)
	{
		String versionHeader = OSType.getOSType() == OSType.Linux ? LINUX_VERSION_HEADER : WINDOWS_VERSION_HEADER;
		Template template = new Template();
		template.add(key ->
		{
			if ("version_header".equals(key))
			{
				return versionHeader;
			}
			if ("thread_config".equals(key))
			{
				return "#define THREAD_COUNT " + threadCount + "\n" +
					"#define FACES_PER_THREAD " + facesPerThread + "\n";
			}
			return null;
		});
		template.addInclude(VRPlugin.class);
		return template;
	}

	private void initProgram() throws com.vr.ShaderException
	{
		Template template = createTemplate(-1, -1);
		glProgram = PROGRAM.compile(template);
		glUiProgram = UI_PROGRAM.compile(template);

		if (computeMode == ComputeMode.OPENGL)
		{
			glComputeProgram = COMPUTE_PROGRAM.compile(createTemplate(1024, 6));
			glSmallComputeProgram = SMALL_COMPUTE_PROGRAM.compile(createTemplate(512, 1));
			glUnorderedComputeProgram = UNORDERED_COMPUTE_PROGRAM.compile(template);
		}
		/*else if (computeMode == ComputeMode.OPENCL)
		{
			openCLManager.init(awtContext);
		}*/

		initUniforms();
	}

	private void initUniforms()
	{

		uniProjection = GL43C.glGetUniformLocation(glProgram, "projection");
		uniView = GL43C.glGetUniformLocation(glProgram, "viewMatrix");
		uniModel = GL43C.glGetUniformLocation(glProgram, "model");

		uniProjectionMatrix = GL43C.glGetUniformLocation(glProgram, "projectionMatrix");
		uniBrightness = GL43C.glGetUniformLocation(glProgram, "brightness");
		uniSmoothBanding = GL43C.glGetUniformLocation(glProgram, "smoothBanding");
		uniUseFog = GL43C.glGetUniformLocation(glProgram, "useFog");
		uniFogColor = GL43C.glGetUniformLocation(glProgram, "fogColor");
		uniFogDepth = GL43C.glGetUniformLocation(glProgram, "fogDepth");
		uniDrawDistance = GL43C.glGetUniformLocation(glProgram, "drawDistance");
		uniExpandedMapLoadingChunks = GL43C.glGetUniformLocation(glProgram, "expandedMapLoadingChunks");
		uniColorBlindMode = GL43C.glGetUniformLocation(glProgram, "colorBlindMode");
		uniTextureLightMode = GL43C.glGetUniformLocation(glProgram, "textureLightMode");
		uniTick = GL43C.glGetUniformLocation(glProgram, "tick");
		uniBlockMain = GL43C.glGetUniformBlockIndex(glProgram, "uniforms");
		uniTextures = GL43C.glGetUniformLocation(glProgram, "textures");
		uniTextureAnimations = GL43C.glGetUniformLocation(glProgram, "textureAnimations");

		uniTex = GL43C.glGetUniformLocation(glUiProgram, "tex");
		uniTexSamplingMode = GL43C.glGetUniformLocation(glUiProgram, "samplingMode");
		uniTexTargetDimensions = GL43C.glGetUniformLocation(glUiProgram, "targetDimensions");
		uniTexSourceDimensions = GL43C.glGetUniformLocation(glUiProgram, "sourceDimensions");
		uniUiColorBlindMode = GL43C.glGetUniformLocation(glUiProgram, "colorBlindMode");
		uniUiAlphaOverlay = GL43C.glGetUniformLocation(glUiProgram, "alphaOverlay");

		uniUiProjection = GL43C.glGetUniformLocation(glUiProgram, "projection");
		uniUiView = GL43C.glGetUniformLocation(glUiProgram, "viewMatrix");

		if (computeMode == ComputeMode.OPENGL)
		{
			uniBlockSmall = GL43C.glGetUniformBlockIndex(glSmallComputeProgram, "uniforms");
			uniBlockLarge = GL43C.glGetUniformBlockIndex(glComputeProgram, "uniforms");
		}
	}

	private void shutdownProgram()
	{
		GL43C.glDeleteProgram(glProgram);
		glProgram = -1;

		GL43C.glDeleteProgram(glComputeProgram);
		glComputeProgram = -1;

		GL43C.glDeleteProgram(glSmallComputeProgram);
		glSmallComputeProgram = -1;

		GL43C.glDeleteProgram(glUnorderedComputeProgram);
		glUnorderedComputeProgram = -1;

		GL43C.glDeleteProgram(glUiProgram);
		glUiProgram = -1;
	}

	private void initVao()
	{
		// Create compute VAO
		vaoCompute = GL43C.glGenVertexArrays();
		GL43C.glBindVertexArray(vaoCompute);

		GL43C.glEnableVertexAttribArray(0);
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, tmpOutBuffer.glBufferId);
		GL43C.glVertexAttribIPointer(0, 4, GL43C.GL_INT, 0, 0);

		GL43C.glEnableVertexAttribArray(1);
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, tmpOutUvBuffer.glBufferId);
		GL43C.glVertexAttribPointer(1, 4, GL43C.GL_FLOAT, false, 0, 0);

		// Create temp VAO
		vaoTemp = GL43C.glGenVertexArrays();
		GL43C.glBindVertexArray(vaoTemp);

		GL43C.glEnableVertexAttribArray(0);
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, tmpVertexBuffer.glBufferId);
		GL43C.glVertexAttribIPointer(0, 4, GL43C.GL_INT, 0, 0);

		GL43C.glEnableVertexAttribArray(1);
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, tmpUvBuffer.glBufferId);
		GL43C.glVertexAttribPointer(1, 4, GL43C.GL_FLOAT, false, 0, 0);

		// Create UI VAO
		vaoUiHandle = GL43C.glGenVertexArrays();
		// Create UI buffer
		vboUiHandle = GL43C.glGenBuffers();
		GL43C.glBindVertexArray(vaoUiHandle);

		FloatBuffer vboUiBuf = com.vr.GpuFloatBuffer.allocateDirect(5 * 4);
		//TODO:figure out placement for this
		vboUiBuf.put(new float[]{
			// positions     // texture coords
			0.2f, 0.2f, -0.2f, 1.0f, 0f, // top right
			0.2f, -0.2f, -0.2f, 1.0f, 1f, // bottom right
			-0.2f, -0.2f, -0.2f, 0.0f, 1f, // bottom left
			-0.2f, 0.2f, -0.2f, 0.0f, 0f  // top left
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

		// unbind VBO
		GL43C.glBindBuffer(GL43C.GL_ARRAY_BUFFER, 0);
	}

	private void shutdownVao()
	{
		GL43C.glDeleteVertexArrays(vaoCompute);
		vaoCompute = -1;

		GL43C.glDeleteVertexArrays(vaoTemp);
		vaoTemp = -1;

		GL43C.glDeleteBuffers(vboUiHandle);
		vboUiHandle = -1;

		GL43C.glDeleteVertexArrays(vaoUiHandle);
		vaoUiHandle = -1;
	}

	private void initBuffers()
	{
		initGlBuffer(sceneVertexBuffer);
		initGlBuffer(sceneUvBuffer);
		initGlBuffer(tmpVertexBuffer);
		initGlBuffer(tmpUvBuffer);
		initGlBuffer(tmpModelBufferLarge);
		initGlBuffer(tmpModelBufferSmall);
		initGlBuffer(tmpModelBufferUnordered);
		initGlBuffer(tmpOutBuffer);
		initGlBuffer(tmpOutUvBuffer);
	}

	private void initGlBuffer(com.vr.GLBuffer glBuffer)
	{
		glBuffer.glBufferId = GL43C.glGenBuffers();
	}

	private void shutdownBuffers()
	{
		destroyGlBuffer(sceneVertexBuffer);
		destroyGlBuffer(sceneUvBuffer);

		destroyGlBuffer(tmpVertexBuffer);
		destroyGlBuffer(tmpUvBuffer);
		destroyGlBuffer(tmpModelBufferLarge);
		destroyGlBuffer(tmpModelBufferSmall);
		destroyGlBuffer(tmpModelBufferUnordered);
		destroyGlBuffer(tmpOutBuffer);
		destroyGlBuffer(tmpOutUvBuffer);
	}

	private void destroyGlBuffer(com.vr.GLBuffer glBuffer)
	{
		if (glBuffer.glBufferId != -1)
		{
			GL43C.glDeleteBuffers(glBuffer.glBufferId);
			glBuffer.glBufferId = -1;
		}
		glBuffer.size = -1;

		if (glBuffer.clBuffer != -1)
		{
			CL12.clReleaseMemObject(glBuffer.clBuffer);
			glBuffer.clBuffer = -1;
		}
	}

	private void initInterfaceTexture()
	{
		interfacePbo = GL43C.glGenBuffers();

		interfaceTexture = GL43C.glGenTextures();
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, interfaceTexture);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_WRAP_S, GL43C.GL_CLAMP_TO_EDGE);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_WRAP_T, GL43C.GL_CLAMP_TO_EDGE);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MIN_FILTER, GL43C.GL_LINEAR);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MAG_FILTER, GL43C.GL_LINEAR);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
	}

	private void shutdownInterfaceTexture()
	{
		GL43C.glDeleteBuffers(interfacePbo);
		GL43C.glDeleteTextures(interfaceTexture);
		interfaceTexture = -1;
	}

	private void initUniformBuffer()
	{
		initGlBuffer(uniformBuffer);

		IntBuffer uniformBuf = com.vr.GpuIntBuffer.allocateDirect(8 + 2048 * 4);
		uniformBuf.put(new int[8]); // uniform block
		final int[] pad = new int[2];
		for (int i = 0; i < 2048; i++)
		{
			uniformBuf.put(Perspective.SINE[i]);
			uniformBuf.put(Perspective.COSINE[i]);
			uniformBuf.put(pad); // ivec2 alignment in std140 is 16 bytes
		}
		uniformBuf.flip();

		updateBuffer(uniformBuffer, GL43C.GL_UNIFORM_BUFFER, uniformBuf, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		GL43C.glBindBuffer(GL43C.GL_UNIFORM_BUFFER, 0);
	}

	/*private void initAAFbo(int width, int height, int aaSamples)
	{
		if (OSType.getOSType() != OSType.MacOS)
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

			width = getScaledValue(transform.getScaleX(), width);
			height = getScaledValue(transform.getScaleY(), height);
		}

		// Create and bind the FBO
		fboSceneHandle = GL43C.glGenFramebuffers();
		GL43C.glBindFramebuffer(GL43C.GL_FRAMEBUFFER, fboSceneHandle);

		// Create color render buffer
		rboSceneHandle = GL43C.glGenRenderbuffers();
		GL43C.glBindRenderbuffer(GL43C.GL_RENDERBUFFER, rboSceneHandle);
		GL43C.glRenderbufferStorageMultisample(GL43C.GL_RENDERBUFFER, aaSamples, GL43C.GL_RGBA, width, height);
		GL43C.glFramebufferRenderbuffer(GL43C.GL_FRAMEBUFFER, GL43C.GL_COLOR_ATTACHMENT0, GL43C.GL_RENDERBUFFER, rboSceneHandle);

		int status = GL43C.glCheckFramebufferStatus(GL43C.GL_FRAMEBUFFER);
		if (status != GL43C.GL_FRAMEBUFFER_COMPLETE)
		{
			throw new RuntimeException("FBO is incomplete. status: " + status);
		}

		// Reset
		GL43C.glBindFramebuffer(GL43C.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
		GL43C.glBindRenderbuffer(GL43C.GL_RENDERBUFFER, 0);
	}*/

	/*private void shutdownAAFbo()
	{
		if (fboSceneHandle != -1)
		{
			GL43C.glDeleteFramebuffers(fboSceneHandle);
			fboSceneHandle = -1;
		}

		if (rboSceneHandle != -1)
		{
			GL43C.glDeleteRenderbuffers(rboSceneHandle);
			rboSceneHandle = -1;
		}
	}*/

	@Override
	public void drawScene(double cameraX, double cameraY, double cameraZ, double cameraPitch, double cameraYaw, int plane)
	{
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.cameraPitch = cameraPitch;
		this.cameraYaw = cameraYaw;
		viewportOffsetX = client.getViewportXOffset();
		viewportOffsetY = client.getViewportYOffset();

		final Scene scene = client.getScene();
		scene.setDrawDistance(getDrawDistance());

		// Only reset the target buffer offset right before drawing the scene. That way if there are frames
		// after this that don't involve a scene draw, like during LOADING/HOPPING/CONNECTION_LOST, we can
		// still redraw the previous frame's scene to emulate the client behavior of not painting over the
		// viewport buffer.
		targetBufferOffset = 0;

		// UBO. Only the first 32 bytes get modified here, the rest is the constant sin/cos table.
		// We can reuse the vertex buffer since it isn't used yet.
		vertexBuffer.clear();
		vertexBuffer.ensureCapacity(32);
		IntBuffer uniformBuf = vertexBuffer.getBuffer();
		uniformBuf
			.put(Float.floatToIntBits((float) cameraYaw))
			.put(Float.floatToIntBits((float) cameraPitch))
			.put(client.getCenterX())
			.put(client.getCenterY())
			.put(client.getScale())
			.put(Float.floatToIntBits((float) cameraX))
			.put(Float.floatToIntBits((float) cameraY))
			.put(Float.floatToIntBits((float) cameraZ));
		uniformBuf.flip();

		GL43C.glBindBuffer(GL43C.GL_UNIFORM_BUFFER, uniformBuffer.glBufferId);
		GL43C.glBufferSubData(GL43C.GL_UNIFORM_BUFFER, 0, uniformBuf);
		GL43C.glBindBuffer(GL43C.GL_UNIFORM_BUFFER, 0);

		GL43C.glBindBufferBase(GL43C.GL_UNIFORM_BUFFER, 0, uniformBuffer.glBufferId);
		uniformBuf.clear();

		checkGLErrors();
	}

	@Override
	public void postDrawScene()
	{
		if (computeMode == ComputeMode.NONE)
		{
			// Upload buffers
			vertexBuffer.flip();
			uvBuffer.flip();

			IntBuffer vertexBuffer = this.vertexBuffer.getBuffer();
			FloatBuffer uvBuffer = this.uvBuffer.getBuffer();

			updateBuffer(tmpVertexBuffer, GL43C.GL_ARRAY_BUFFER, vertexBuffer, GL43C.GL_DYNAMIC_DRAW, 0L);
			updateBuffer(tmpUvBuffer, GL43C.GL_ARRAY_BUFFER, uvBuffer, GL43C.GL_DYNAMIC_DRAW, 0L);

			checkGLErrors();
			return;
		}

		// Upload buffers
		vertexBuffer.flip();
		uvBuffer.flip();
		modelBuffer.flip();
		modelBufferSmall.flip();
		modelBufferUnordered.flip();

		IntBuffer vertexBuffer = this.vertexBuffer.getBuffer();
		FloatBuffer uvBuffer = this.uvBuffer.getBuffer();
		IntBuffer modelBuffer = this.modelBuffer.getBuffer();
		IntBuffer modelBufferSmall = this.modelBufferSmall.getBuffer();
		IntBuffer modelBufferUnordered = this.modelBufferUnordered.getBuffer();

		// temp buffers
		updateBuffer(tmpVertexBuffer, GL43C.GL_ARRAY_BUFFER, vertexBuffer, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpUvBuffer, GL43C.GL_ARRAY_BUFFER, uvBuffer, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

		// model buffers
		updateBuffer(tmpModelBufferLarge, GL43C.GL_ARRAY_BUFFER, modelBuffer, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpModelBufferSmall, GL43C.GL_ARRAY_BUFFER, modelBufferSmall, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpModelBufferUnordered, GL43C.GL_ARRAY_BUFFER, modelBufferUnordered, GL43C.GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

		// Output buffers
		updateBuffer(tmpOutBuffer,
			GL43C.GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is an ivec4, which is 16 bytes
			GL43C.GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);
		updateBuffer(tmpOutUvBuffer,
			GL43C.GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is a vec4, which is 16 bytes
			GL43C.GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);

		if (computeMode == ComputeMode.OPENCL)
		{
			// The docs for clEnqueueAcquireGLObjects say all pending GL operations must be completed before calling
			// clEnqueueAcquireGLObjects, and recommends calling glFinish() as the only portable way to do that.
			// However no issues have been observed from not calling it, and so will leave disabled for now.
			// GL43C.glFinish();

			openCLManager.compute(
				unorderedModels, smallModels, largeModels,
				sceneVertexBuffer, sceneUvBuffer,
				tmpVertexBuffer, tmpUvBuffer,
				tmpModelBufferUnordered, tmpModelBufferSmall, tmpModelBufferLarge,
				tmpOutBuffer, tmpOutUvBuffer,
				uniformBuffer);

			checkGLErrors();
			return;
		}

		/*
		 * Compute is split into three separate programs: 'unordered', 'small', and 'large'
		 * to save on GPU resources. Small will sort <= 512 faces, large will do <= 6144.
		 */

		// Bind UBO to compute programs
		GL43C.glUniformBlockBinding(glSmallComputeProgram, uniBlockSmall, 0);
		GL43C.glUniformBlockBinding(glComputeProgram, uniBlockLarge, 0);

		// unordered
		GL43C.glUseProgram(glUnorderedComputeProgram);

		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, tmpModelBufferUnordered.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, sceneVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, tmpVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 3, tmpOutBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 4, tmpOutUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 5, sceneUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 6, tmpUvBuffer.glBufferId);

		GL43C.glDispatchCompute(unorderedModels, 1, 1);

		// small
		GL43C.glUseProgram(glSmallComputeProgram);

		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, tmpModelBufferSmall.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, sceneVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, tmpVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 3, tmpOutBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 4, tmpOutUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 5, sceneUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 6, tmpUvBuffer.glBufferId);

		GL43C.glDispatchCompute(smallModels, 1, 1);

		// large
		GL43C.glUseProgram(glComputeProgram);

		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, tmpModelBufferLarge.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, sceneVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, tmpVertexBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 3, tmpOutBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 4, tmpOutUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 5, sceneUvBuffer.glBufferId);
		GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 6, tmpUvBuffer.glBufferId);

		GL43C.glDispatchCompute(largeModels, 1, 1);

		checkGLErrors();
	}

	@Override
	public void drawScenePaint(int orientation, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z,
		SceneTilePaint paint, int tileZ, int tileX, int tileY,
		int zoom, int centerX, int centerY)
	{
		if (computeMode == ComputeMode.NONE)
		{
			targetBufferOffset += sceneUploader.upload(client.getScene(), paint,
				tileZ, tileX, tileY,
				vertexBuffer, uvBuffer,
				tileX << Perspective.LOCAL_COORD_BITS,
				tileY << Perspective.LOCAL_COORD_BITS,
				true
			);
		}
		else if (paint.getBufferLen() > 0)
		{
			final int localX = tileX << Perspective.LOCAL_COORD_BITS;
			final int localY = 0;
			final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

			com.vr.GpuIntBuffer b = modelBufferUnordered;
			++unorderedModels;

			b.ensureCapacity(8);
			IntBuffer buffer = b.getBuffer();
			buffer.put(paint.getBufferOffset());
			buffer.put(paint.getUvBufferOffset());
			buffer.put(2);
			buffer.put(targetBufferOffset);
			buffer.put(FLAG_SCENE_BUFFER);
			buffer.put(localX).put(localY).put(localZ);

			targetBufferOffset += 2 * 3;
		}
	}

	@Override
	public void drawSceneModel(int orientation, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z,
		SceneTileModel model, int tileZ, int tileX, int tileY,
		int zoom, int centerX, int centerY)
	{
		if (computeMode == ComputeMode.NONE)
		{
			targetBufferOffset += sceneUploader.upload(model,
				0, 0,
				vertexBuffer, uvBuffer,
				true);
		}
		else if (model.getBufferLen() > 0)
		{
			final int localX = tileX << Perspective.LOCAL_COORD_BITS;
			final int localY = 0;
			final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

			com.vr.GpuIntBuffer b = modelBufferUnordered;
			++unorderedModels;

			b.ensureCapacity(8);
			IntBuffer buffer = b.getBuffer();
			buffer.put(model.getBufferOffset());
			buffer.put(model.getUvBufferOffset());
			buffer.put(model.getBufferLen() / 3);
			buffer.put(targetBufferOffset);
			buffer.put(FLAG_SCENE_BUFFER);
			buffer.put(localX).put(localY).put(localZ);

			targetBufferOffset += model.getBufferLen();
		}
	}

	private void prepareInterfaceTexture(int canvasWidth, int canvasHeight)
	{
		if (canvasWidth != lastCanvasWidth || canvasHeight != lastCanvasHeight)
		{
			lastCanvasWidth = canvasWidth;
			lastCanvasHeight = canvasHeight;

			GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, interfacePbo);
			GL43C.glBufferData(GL43C.GL_PIXEL_UNPACK_BUFFER, canvasWidth * canvasHeight * 4L, GL43C.GL_STREAM_DRAW);
			GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, 0);

			GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, interfaceTexture);
			GL43C.glTexImage2D(GL43C.GL_TEXTURE_2D, 0, GL43C.GL_RGBA, canvasWidth, canvasHeight, 0, GL43C.GL_BGRA, GL43C.GL_UNSIGNED_BYTE, 0);
			GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
		}

		final BufferProvider bufferProvider = client.getBufferProvider();
		final int[] pixels = bufferProvider.getPixels();
		final int width = bufferProvider.getWidth();
		final int height = bufferProvider.getHeight();

		GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, interfacePbo);
		GL43C.glMapBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, GL43C.GL_WRITE_ONLY)
			.asIntBuffer()
			.put(pixels, 0, width * height);
		GL43C.glUnmapBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, interfaceTexture);
		GL43C.glTexSubImage2D(GL43C.GL_TEXTURE_2D, 0, 0, 0, width, height, GL43C.GL_BGRA, GL43C.GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		GL43C.glBindBuffer(GL43C.GL_PIXEL_UNPACK_BUFFER, 0);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
	}

	private void renderFrameOpenXR(int sky, float brightness, GameState gameState,int overlayColor, float viewportWidth, float viewportHeight) {
		pollEvents();
		try (MemoryStack stack = stackPush()) {
			XrFrameState frameState = XrFrameState.calloc(stack)
					.type$Default();

			check(xrWaitFrame(
					xrSession,
					XrFrameWaitInfo.calloc(stack)
							.type$Default(),
					frameState
			));

			check(xrBeginFrame(
					xrSession,
					XrFrameBeginInfo.calloc(stack)
							.type$Default()
			));

			XrCompositionLayerProjection layerProjection = XrCompositionLayerProjection.calloc(stack)
					.type$Default();

			PointerBuffer layers = stack.callocPointer(1);

			boolean didRender = false;
			System.out.println(frameState.shouldRender()+" "+frameState.predictedDisplayTime());
			if (frameState.shouldRender()) {
				if (renderLayerOpenXR(sky, brightness, gameState,stack, frameState.predictedDisplayTime(), layerProjection, viewportWidth, viewportHeight, overlayColor)) {
					layers.put(0, layerProjection);
					didRender = true;
				} else {
					System.out.println("Didn't render");
				}
			} else {
				System.out.println("Shouldn't render");
			}

			check(xrEndFrame(
					xrSession,
					XrFrameEndInfo.malloc(stack)
							.type$Default()
							.next(NULL)
							.displayTime(frameState.predictedDisplayTime())
							.environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
							.layers(didRender ? layers : null)
							.layerCount(didRender ? layers.remaining() : 0)
			));
		}
	}

	private boolean renderLayerOpenXR(int sky, float brightness, GameState gameState,MemoryStack stack, long predictedDisplayTime, XrCompositionLayerProjection layer, float viewportWidth, float viewportHeight, int overlayColor) {
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

		int viewCountOutput = pi.get(0);
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
			Swapchain viewSwapchain = swapchains[viewIndex];

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

			OpenGLRenderView(sky, brightness, gameState,projectionLayerView, viewSwapchain.images.get(swapchainImageIndex), viewIndex, viewportWidth, viewportHeight, overlayColor);

			check(xrReleaseSwapchainImage(
					viewSwapchain.handle,
					XrSwapchainImageReleaseInfo.calloc(stack)
							.type$Default()
			));
		}

		layer.space(xrAppSpace);
		layer.views(projectionLayerViews);
		return true;
	}

	private static FloatBuffer mvpMatrix = BufferUtils.createFloatBuffer(16);
	//int screenShader = ShadersGL.createShaderProgram(ShadersGL.screenVertShader, ShadersGL.texFragShader);

	private void OpenGLRenderView(int sky, float brightness, GameState gameState, XrCompositionLayerProjectionView layerView, XrSwapchainImageOpenGLKHR swapchainImage, int viewIndex, float viewportWidth, float viewportHeight, int overlayColor) {
		GL43C.glUseProgram(glProgram);

		final int drawDistance = getDrawDistance();
		final int fogDepth = config.fogDepth();
		GL43C.glUniform1i(uniUseFog, fogDepth > 0 ? 1 : 0);
		GL43C.glUniform4f(uniFogColor, (sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		GL43C.glUniform1i(uniFogDepth, fogDepth);
		GL43C.glUniform1i(uniDrawDistance, drawDistance * Perspective.LOCAL_TILE_SIZE);
		GL43C.glUniform1i(uniExpandedMapLoadingChunks, client.getExpandedMapLoading());

		// Brightness happens to also be stored in the texture provider, so we use that
		GL43C.glUniform1f(uniBrightness, brightness);
		GL43C.glUniform1f(uniSmoothBanding, config.smoothBanding() ? 0f : 1f);
		GL43C.glUniform1i(uniColorBlindMode, config.colorBlindMode().ordinal());
		GL43C.glUniform1f(uniTextureLightMode, config.brightTextures() ? 1f : 0f);
		if (gameState == GameState.LOGGED_IN)
		{
			// avoid textures animating during loading
			GL43C.glUniform1i(uniTick, client.getGameCycle());
		}

		// Bind uniforms
		GL43C.glUniformBlockBinding(glProgram, uniBlockMain, 0);
		GL43C.glUniform1i(uniTextures, 1); // texture sampler array is bound to texture1

		// We just allow the GL to do face culling. Note this requires the priority renderer
		// to have logic to disregard culled faces in the priority depth testing.
		GL43C.glEnable(GL43C.GL_CULL_FACE);

		// Enable blending for alpha
		GL43C.glEnable(GL43C.GL_BLEND);
		GL43C.glBlendFuncSeparate(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA, GL43C.GL_ONE, GL43C.GL_ONE);

		// Draw buffers
		if (computeMode != ComputeMode.NONE)
		{
			if (computeMode == ComputeMode.OPENGL)
			{
				// Before reading the SSBOs written to from postDrawScene() we must insert a barrier
				GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
			}
			else
			{
				// Wait for the command queue to finish, so that we know the compute is done
				openCLManager.finish();
			}

			// Draw using the output buffer of the compute
			GL43C.glBindVertexArray(vaoCompute);
		}
		else
		{
			// Only use the temporary buffers, which will contain the full scene
			GL43C.glBindVertexArray(vaoTemp);
		}

		/*THE ACTUAL START OF THE CODE*/
		glBindFramebuffer(GL_FRAMEBUFFER, swapchainFramebuffer);

		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, swapchainImage.image(), 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextures.get(swapchainImage), 0);

		XrRect2Di imageRect = layerView.subImage().imageRect();
		System.out.println(imageRect.offset().x()+" "+imageRect.offset().y()+" "+imageRect.extent().width()+" "+imageRect.extent().height());
		glViewport(
				imageRect.offset().x(),
				imageRect.offset().y(),
				imageRect.extent().width(),
				imageRect.extent().height()
		);

		float[] DarkSlateGray = {0.184313729f, 0.309803933f, 0.309803933f};
		glClearColor(DarkSlateGray[0], DarkSlateGray[1], DarkSlateGray[2], 1.0f);
		glClearDepth(1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		glFrontFace(GL_CW);
		glCullFace(GL_BACK);
		glEnable(GL_DEPTH_TEST);

		XrPosef       pose        = layerView.pose();
		XrVector3f    pos         = pose.position$();
		XrQuaternionf orientation = pose.orientation();


		//glUniformMatrix4fv(uniModel, false, modelviewMatrix.get(mvpMatrix));

		//XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 100f, false);
		//viewMatrix.translationRotateScaleInvert(
		//		pos.x(), pos.y(), pos.z(),
		//		orientation.x(), orientation.y(), orientation.z(), orientation.w(),
		//		1, 1, 1
		//);

		glDisable(GL_CULL_FACE); // Disable back-face culling so we can see the inside of the world-space cube and backside of the plane

		//modelviewMatrix.identity();
		//XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 100f, false);




		/*System.out.println("HERE: "+layerView.fov().angleDown()+" "+layerView.fov().angleUp()+" "+layerView.fov().angleLeft()+" "+layerView.fov().angleRight());
		XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 100f, false);
		System.out.println("("+pos.x()+","+pos.y()+","+pos.z()+")"+"("+orientation.x()+","+orientation.y()+","+orientation.z()+","+orientation.w()+")");
		viewMatrix.translationRotateScaleInvert(
				0, 0, 0,
				orientation.x(), orientation.y(), orientation.z(), orientation.w(),
				1, 1,1
		);
		//glUniformMatrix4fv(uniProjection, false, projectionMatrix.identity().get(mvpMatrix));


		float[] projectionMatrix = com.vr.Mat4.identity();
		com.vr.Mat4.mul(projectionMatrix, Mat4.translate((float) -pos.x(), (float) -pos.y(), (float) -pos.z()));
		GL43C.glUniformMatrix4fv(uniProjectionMatrix, false, projectionMatrix);

		GL43C.glUniformMatrix4fv(uniView, false, viewMatrix.get(mvpMatrix));

		float[] projectionMatrix2 = com.vr.Mat4.identity();
		com.vr.Mat4.mul(projectionMatrix2, Mat4.scale(client.getScale(), client.getScale(), 1));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.projection(viewportWidth, viewportHeight, 50));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateY((float) cameraYaw));
		com.vr.Mat4.mul(projectionMatrix2, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));
		GL43C.glUniformMatrix4fv(uniProjection, false, projectionMatrix2);*/




		//modelviewMatrix.identity();
		//XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 100f, false);
		System.out.println("HERE: "+layerView.fov().angleDown()+" "+layerView.fov().angleUp()+" "+layerView.fov().angleLeft()+" "+layerView.fov().angleRight());
		XRHelper.applyProjectionToMatrix(projectionMatrix.identity(), layerView.fov(), 0.1f, 10000f, false);
		System.out.println("("+pos.x()+","+pos.y()+","+pos.z()+")"+"("+orientation.x()+","+orientation.y()+","+orientation.z()+","+orientation.w()+")");
		viewMatrix.translationRotateScaleInvert(
				(float) pos.x(), (float) pos.y(), (float) pos.z(),
				orientation.x(), orientation.y(), orientation.z(), orientation.w(),
				1, 1,1
		);
		//glUniformMatrix4fv(uniProjection, false, projectionMatrix.identity().get(mvpMatrix));
		/*float[] projectionMatrix2 = com.vr.Mat4.identity();
		com.vr.Mat4.mul(projectionMatrix2, Mat4.scale(client.getScale(), client.getScale(), -(float)client.getScale()/181f));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.projection(viewportWidth, viewportHeight, 250));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateY((float) cameraYaw));
		com.vr.Mat4.mul(projectionMatrix2, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ+1000f*(float)(1448-client.getScale())/1448f));*/

		float[] projectionMatrix2 = com.vr.Mat4.identity();
		com.vr.Mat4.mul(projectionMatrix2, Mat4.scale(client.getScale(), client.getScale(), -1));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.projection(viewportWidth, viewportHeight, 250));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
		com.vr.Mat4.mul(projectionMatrix2, com.vr.Mat4.rotateY((float) cameraYaw));
		com.vr.Mat4.mul(projectionMatrix2, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));

		//float[] projectionMatrix = com.vr.Mat4.identity();
		//com.vr.Mat4.mul(projectionMatrix, Mat4.translate((float) -pos.x(), (float) -pos.y(), (float) -pos.z()));


		GL43C.glUniformMatrix4fv(uniProjectionMatrix, false, projectionMatrix2);
		GL43C.glUniformMatrix4fv(uniView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniProjection, false, projectionMatrix.get(mvpMatrix));

		GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 0, targetBufferOffset);

		drawUi(overlayColor, 100, 100, viewMatrix, projectionMatrix);

		glEnable(GL_CULL_FACE);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		/*if (viewIndex == 0 || true) { // The view to the GLFW window
			try (MemoryStack stack = stackPush()) {
				Swapchain swapchain = swapchains[viewIndex];

				IntBuffer ww = stack.mallocInt(1);
				IntBuffer wh = stack.mallocInt(1);
				glfwGetWindowSize(window, ww, wh);

				int wh2 = (int)(((float)swapchain.height / swapchain.width) * ww.get(0));
				if (wh2 > wh.get(0)) {
					int ww2 = (int)(((float)swapchain.width / swapchain.height) * wh.get(0));
					glViewport(ww2 * viewIndex, 0, ww2, wh.get(0));
				} else {
					glViewport(ww.get(0) * viewIndex, 0, ww.get(0), wh2);
				}
			}
			glFrontFace(GL_CCW);
			glUseProgram(screenShader);
			//glBindVertexArray(quadVAO);
			glDisable(GL_DEPTH_TEST);
			glBindTexture(GL_TEXTURE_2D, swapchainImage.image());
			glDrawArrays(GL_TRIANGLES, 0, 6);*/
			if (viewIndex == swapchains.length - 1) {
				glFlush();
			}
		//}
	}

	@Override
	public void draw(int overlayColor)
	{
		final GameState gameState = client.getGameState();
		if (gameState == GameState.STARTING)
		{
			return;
		}

		final int canvasHeight = client.getCanvasHeight();
		final int canvasWidth = client.getCanvasWidth();

		final int viewportHeight = client.getViewportHeight();
		final int viewportWidth = client.getViewportWidth();

		prepareInterfaceTexture(canvasWidth, canvasHeight);

		// Setup anti-aliasing
		final AntiAliasingMode antiAliasingMode = config.antiAliasingMode();
		final boolean aaEnabled = antiAliasingMode != AntiAliasingMode.DISABLED;

		/*if (aaEnabled)
		{
			GL43C.glEnable(GL43C.GL_MULTISAMPLE);

			final Dimension stretchedDimensions = client.getStretchedDimensions();

			final int stretchedCanvasWidth = client.isStretchedEnabled() ? stretchedDimensions.width : canvasWidth;
			final int stretchedCanvasHeight = client.isStretchedEnabled() ? stretchedDimensions.height : canvasHeight;

			// Re-create fbo
			if (lastStretchedCanvasWidth != stretchedCanvasWidth
				|| lastStretchedCanvasHeight != stretchedCanvasHeight
				|| lastAntiAliasingMode != antiAliasingMode)
			{
				shutdownAAFbo();

				// Bind default FBO to check whether anti-aliasing is forced
				GL43C.glBindFramebuffer(GL43C.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
				final int forcedAASamples = GL43C.glGetInteger(GL43C.GL_SAMPLES);
				final int maxSamples = GL43C.glGetInteger(GL43C.GL_MAX_SAMPLES);
				final int samples = forcedAASamples != 0 ? forcedAASamples :
					Math.min(antiAliasingMode.getSamples(), maxSamples);

				log.debug("AA samples: {}, max samples: {}, forced samples: {}", samples, maxSamples, forcedAASamples);

				initAAFbo(stretchedCanvasWidth, stretchedCanvasHeight, samples);

				lastStretchedCanvasWidth = stretchedCanvasWidth;
				lastStretchedCanvasHeight = stretchedCanvasHeight;
			}

			GL43C.glBindFramebuffer(GL43C.GL_DRAW_FRAMEBUFFER, fboSceneHandle);
		}
		else
		{
			GL43C.glDisable(GL43C.GL_MULTISAMPLE);
			shutdownAAFbo();
		}*/

		lastAntiAliasingMode = antiAliasingMode;

		// Clear scene
		int sky = client.getSkyboxColor();
		GL43C.glClearColor((sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		GL43C.glClear(GL43C.GL_COLOR_BUFFER_BIT);

		// Draw 3d scene
		if (gameState.getState() >= GameState.LOADING.getState())
		{
			final TextureProvider textureProvider = client.getTextureProvider();
			if (textureArrayId == -1)
			{
				// lazy init textures as they may not be loaded at plugin start.
				// this will return -1 and retry if not all textures are loaded yet, too.
				textureArrayId = textureManager.initTextureArray(textureProvider);
				if (textureArrayId > -1)
				{
					// if texture upload is successful, compute and set texture animations
					float[] texAnims = textureManager.computeTextureAnimations(textureProvider);
					GL43C.glUseProgram(glProgram);
					GL43C.glUniform2fv(uniTextureAnimations, texAnims);
					GL43C.glUseProgram(0);
				}
			}

			int renderWidthOff = viewportOffsetX;
			int renderHeightOff = viewportOffsetY;
			int renderCanvasHeight = canvasHeight;
			int renderViewportHeight = viewportHeight;
			int renderViewportWidth = viewportWidth;

			// Setup anisotropic filtering
			final int anisotropicFilteringLevel = config.anisotropicFilteringLevel();

			if (textureArrayId != -1 && lastAnisotropicFilteringLevel != anisotropicFilteringLevel)
			{
				textureManager.setAnisotropicFilteringLevel(textureArrayId, anisotropicFilteringLevel);
				lastAnisotropicFilteringLevel = anisotropicFilteringLevel;
			}

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

			glDpiAwareViewport(renderWidthOff, renderCanvasHeight - renderViewportHeight - renderHeightOff, renderViewportWidth, renderViewportHeight);

			/*GL43C.glUseProgram(glProgram);

			final int drawDistance = getDrawDistance();
			final int fogDepth = config.fogDepth();
			GL43C.glUniform1i(uniUseFog, fogDepth > 0 ? 1 : 0);
			GL43C.glUniform4f(uniFogColor, (sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
			GL43C.glUniform1i(uniFogDepth, fogDepth);
			GL43C.glUniform1i(uniDrawDistance, drawDistance * Perspective.LOCAL_TILE_SIZE);
			GL43C.glUniform1i(uniExpandedMapLoadingChunks, client.getExpandedMapLoading());

			// Brightness happens to also be stored in the texture provider, so we use that
			GL43C.glUniform1f(uniBrightness, (float) textureProvider.getBrightness());
			GL43C.glUniform1f(uniSmoothBanding, config.smoothBanding() ? 0f : 1f);
			GL43C.glUniform1i(uniColorBlindMode, config.colorBlindMode().ordinal());
			GL43C.glUniform1f(uniTextureLightMode, config.brightTextures() ? 1f : 0f);
			if (gameState == GameState.LOGGED_IN)
			{
				// avoid textures animating during loading
				GL43C.glUniform1i(uniTick, client.getGameCycle());
			}

			System.out.println("WOOP: "+cameraX+" "+cameraY+" "+cameraZ+" "+client.getScale());
			// Calculate projection matrix
			//float[] projectionMatrix = com.vr.Mat4.scale(client.getScale(), client.getScale(), 1);
			float[] projectionMatrix = com.vr.Mat4.identity();
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.projection(viewportWidth, viewportHeight, 50));
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateY((float) cameraYaw));
			com.vr.Mat4.mul(projectionMatrix, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));
			GL43C.glUniformMatrix4fv(uniProjectionMatrix, false, projectionMatrix);

			// Bind uniforms
			GL43C.glUniformBlockBinding(glProgram, uniBlockMain, 0);
			GL43C.glUniform1i(uniTextures, 1); // texture sampler array is bound to texture1

			// We just allow the GL to do face culling. Note this requires the priority renderer
			// to have logic to disregard culled faces in the priority depth testing.
			GL43C.glEnable(GL43C.GL_CULL_FACE);

			// Enable blending for alpha
			GL43C.glEnable(GL43C.GL_BLEND);
			GL43C.glBlendFuncSeparate(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA, GL43C.GL_ONE, GL43C.GL_ONE);

			// Draw buffers
			if (computeMode != ComputeMode.NONE)
			{
				if (computeMode == ComputeMode.OPENGL)
				{
					// Before reading the SSBOs written to from postDrawScene() we must insert a barrier
					GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
				}
				else
				{
					// Wait for the command queue to finish, so that we know the compute is done
					openCLManager.finish();
				}

				// Draw using the output buffer of the compute
				GL43C.glBindVertexArray(vaoCompute);
			}
			else
			{
				// Only use the temporary buffers, which will contain the full scene
				GL43C.glBindVertexArray(vaoTemp);
			}*/

			renderFrameOpenXR(sky, (float)textureProvider.getBrightness(), gameState,overlayColor, viewportWidth, viewportHeight);

			/*projectionMatrix = com.vr.Mat4.scale(client.getScale(), client.getScale(), 1);
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.projection(viewportWidth, viewportHeight, 50));
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateX((float) -(Math.PI - cameraPitch)));
			com.vr.Mat4.mul(projectionMatrix, com.vr.Mat4.rotateY((float) 0));
			com.vr.Mat4.mul(projectionMatrix, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));
			GL43C.glUniformMatrix4fv(uniProjectionMatrix, false, projectionMatrix);

			GL43C.glDrawArrays(GL43C.GL_TRIANGLES, 0, targetBufferOffset);*/
			////openXR.renderFrameOpenXR();

			GL43C.glDisable(GL43C.GL_BLEND);
			GL43C.glDisable(GL43C.GL_CULL_FACE);

			GL43C.glUseProgram(0);
		}

		/*if (aaEnabled)
		{
			int width = lastStretchedCanvasWidth;
			int height = lastStretchedCanvasHeight;

			if (OSType.getOSType() != OSType.MacOS)
			{
				final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
				final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

				width = getScaledValue(transform.getScaleX(), width);
				height = getScaledValue(transform.getScaleY(), height);
			}

			GL43C.glBindFramebuffer(GL43C.GL_READ_FRAMEBUFFER, fboSceneHandle);
			GL43C.glBindFramebuffer(GL43C.GL_DRAW_FRAMEBUFFER, awtContext.getFramebuffer(false));
			GL43C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
				GL43C.GL_COLOR_BUFFER_BIT, GL43C.GL_NEAREST);

			// Reset
			GL43C.glBindFramebuffer(GL43C.GL_READ_FRAMEBUFFER, awtContext.getFramebuffer(false));
		}*/

		vertexBuffer.clear();
		uvBuffer.clear();
		modelBuffer.clear();
		modelBufferSmall.clear();
		modelBufferUnordered.clear();

		smallModels = largeModels = unorderedModels = 0;
		tempOffset = 0;
		tempUvOffset = 0;

		// Texture on UI
		//drawUi(overlayColor, canvasHeight, canvasWidth);

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

			throw ex;
		}*/

		//drawManager.processDrawComplete(this::screenshot);

		////GL43C.glBindFramebuffer(GL43C.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));

		checkGLErrors();
	}

	//TODO: Move this inside the XR rendering.
	private void drawUi(final int overlayColor, final int canvasHeight, final int canvasWidth, Matrix4f viewMatrix, Matrix4f projectionMatrix)
	{
		GL43C.glEnable(GL43C.GL_BLEND);
		GL43C.glBlendFunc(GL43C.GL_ONE, GL43C.GL_ONE_MINUS_SRC_ALPHA);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, interfaceTexture);

		// Use the texture bound in the first pass
		final UIScalingMode uiScalingMode = config.uiScalingMode();
		GL43C.glUseProgram(glUiProgram);
		GL43C.glUniformMatrix4fv(uniUiView, false, viewMatrix.get(mvpMatrix));
		GL43C.glUniformMatrix4fv(uniUiProjection, false, projectionMatrix.get(mvpMatrix));
		GL43C.glUniform1i(uniTex, 0);
		GL43C.glUniform1i(uniTexSamplingMode, uiScalingMode.getMode());
		GL43C.glUniform2i(uniTexSourceDimensions, canvasWidth, canvasHeight);
		GL43C.glUniform1i(uniUiColorBlindMode, config.colorBlindMode().ordinal());
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
			final int function = uiScalingMode == UIScalingMode.LINEAR ? GL43C.GL_LINEAR : GL43C.GL_NEAREST;
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

		if (OSType.getOSType() != OSType.MacOS)
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			width = getScaledValue(t.getScaleX(), width);
			height = getScaledValue(t.getScaleY(), height);
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
			.order(ByteOrder.nativeOrder());

		GL43C.glReadBuffer(awtContext.getBufferMode());
		GL43C.glReadPixels(0, 0, width, height, GL43C.GL_RGBA, GL43C.GL_UNSIGNED_BYTE, buffer);

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

	@Override
	public void animate(Texture texture, int diff)
	{
		// texture animation happens on gpu
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
			targetBufferOffset = 0;
		}
	}

	@Override
	public void loadScene(Scene scene)
	{
		if (computeMode == ComputeMode.NONE)
		{
			return;
		}

		com.vr.GpuIntBuffer vertexBuffer = new com.vr.GpuIntBuffer();
		com.vr.GpuFloatBuffer uvBuffer = new com.vr.GpuFloatBuffer();

		sceneUploader.upload(scene, vertexBuffer, uvBuffer);

		vertexBuffer.flip();
		uvBuffer.flip();

		nextSceneVertexBuffer = vertexBuffer;
		nextSceneTexBuffer = uvBuffer;
		nextSceneId = sceneUploader.sceneId;
	}

	private void uploadTileHeights(Scene scene)
	{
		if (tileHeightTex != 0)
		{
			GL43C.glDeleteTextures(tileHeightTex);
			tileHeightTex = 0;
		}

		final int TILEHEIGHT_BUFFER_SIZE = Constants.MAX_Z * Constants.EXTENDED_SCENE_SIZE * Constants.EXTENDED_SCENE_SIZE * Short.BYTES;
		ShortBuffer tileBuffer = ByteBuffer
			.allocateDirect(TILEHEIGHT_BUFFER_SIZE)
			.order(ByteOrder.nativeOrder())
			.asShortBuffer();

		int[][][] tileHeights = scene.getTileHeights();
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
			{
				for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
				{
					int h = tileHeights[z][x][y];
					assert (h & 0b111) == 0;
					h >>= 3;
					tileBuffer.put((short) h);
				}
			}
		}
		tileBuffer.flip();

		tileHeightTex = GL43C.glGenTextures();
		GL43C.glBindTexture(GL43C.GL_TEXTURE_3D, tileHeightTex);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_3D, GL43C.GL_TEXTURE_MIN_FILTER, GL43C.GL_NEAREST);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_3D, GL43C.GL_TEXTURE_MAG_FILTER, GL43C.GL_NEAREST);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_3D, GL43C.GL_TEXTURE_WRAP_S, GL43C.GL_CLAMP_TO_EDGE);
		GL43C.glTexParameteri(GL43C.GL_TEXTURE_3D, GL43C.GL_TEXTURE_WRAP_T, GL43C.GL_CLAMP_TO_EDGE);
		GL43C.glTexImage3D(GL43C.GL_TEXTURE_3D, 0, GL43C.GL_R16I,
			Constants.EXTENDED_SCENE_SIZE, Constants.EXTENDED_SCENE_SIZE, Constants.MAX_Z,
			0, GL43C.GL_RED_INTEGER, GL43C.GL_SHORT, tileBuffer);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_3D, 0);

		// bind to texture 2
		GL43C.glActiveTexture(GL43C.GL_TEXTURE2);
		GL43C.glBindTexture(GL43C.GL_TEXTURE_3D, tileHeightTex); // binding = 2 in the shader
		GL43C.glActiveTexture(GL43C.GL_TEXTURE0);
	}

	@Override
	public void swapScene(Scene scene)
	{
		if (computeMode == ComputeMode.NONE)
		{
			return;
		}

		if (computeMode == ComputeMode.OPENCL)
		{
			openCLManager.uploadTileHeights(scene);
		}
		else
		{
			assert computeMode == ComputeMode.OPENGL;
			uploadTileHeights(scene);
		}

		sceneId = nextSceneId;
		updateBuffer(sceneVertexBuffer, GL43C.GL_ARRAY_BUFFER, nextSceneVertexBuffer.getBuffer(), GL43C.GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
		updateBuffer(sceneUvBuffer, GL43C.GL_ARRAY_BUFFER, nextSceneTexBuffer.getBuffer(), GL43C.GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);

		nextSceneVertexBuffer = null;
		nextSceneTexBuffer = null;
		nextSceneId = -1;

		checkGLErrors();
	}

	@Override
	public boolean tileInFrustum(Scene scene, int pitchSin, int pitchCos, int yawSin, int yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy)
	{
		int[][][] tileHeights = scene.getTileHeights();
		int x = ((msx - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraX;
		int z = ((msy - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraZ;
		int y = Math.max(
			Math.max(tileHeights[plane][msx][msy], tileHeights[plane][msx][msy + 1]),
			Math.max(tileHeights[plane][msx + 1][msy], tileHeights[plane][msx + 1][msy + 1])
		) + GROUND_MIN_Y - cameraY;

		int radius = 96; // ~ 64 * sqrt(2)

		int zoom = client.get3dZoom();
		int Rasterizer3D_clipMidX2 = client.getRasterizer3D_clipMidX2();
		int Rasterizer3D_clipNegativeMidX = client.getRasterizer3D_clipNegativeMidX();
		int Rasterizer3D_clipNegativeMidY = client.getRasterizer3D_clipNegativeMidY();

		int var11 = yawCos * z - yawSin * x >> 16;
		int var12 = pitchSin * y + pitchCos * var11 >> 16;
		int var13 = pitchCos * radius >> 16;
		int depth = var12 + var13;
		if (depth > 50)
		{
			int rx = z * yawSin + yawCos * x >> 16;
			int var16 = (rx - radius) * zoom;
			int var17 = (rx + radius) * zoom;
			// left && right
			if (var16 < Rasterizer3D_clipMidX2 * depth && var17 > Rasterizer3D_clipNegativeMidX * depth)
			{
				int ry = pitchCos * y - var11 * pitchSin >> 16;
				int ybottom = pitchSin * radius >> 16;
				int var20 = (ry + ybottom) * zoom;
				// top
				if (var20 > Rasterizer3D_clipNegativeMidY * depth)
				{
					// we don't test the bottom so we don't have to find the height of all the models on the tile
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Check is a model is visible and should be drawn.
	 */
	private boolean isVisible(Model model, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z)
	{
		model.calculateBoundsCylinder();

		final int xzMag = model.getXYZMag();
		final int bottomY = model.getBottomY();
		final int zoom = client.get3dZoom();
		final int modelHeight = model.getModelHeight();

		int Rasterizer3D_clipMidX2 = client.getRasterizer3D_clipMidX2(); // width / 2
		int Rasterizer3D_clipNegativeMidX = client.getRasterizer3D_clipNegativeMidX(); // -width / 2
		int Rasterizer3D_clipNegativeMidY = client.getRasterizer3D_clipNegativeMidY(); // -height / 2
		int Rasterizer3D_clipMidY2 = client.getRasterizer3D_clipMidY2(); // height / 2

		int var11 = yawCos * z - yawSin * x >> 16;
		int var12 = pitchSin * y + pitchCos * var11 >> 16;
		int var13 = pitchCos * xzMag >> 16;
		int depth = var12 + var13;
		if (depth > 50)
		{
			int rx = z * yawSin + yawCos * x >> 16;
			int var16 = (rx - xzMag) * zoom;
			if (var16 / depth < Rasterizer3D_clipMidX2)
			{
				int var17 = (rx + xzMag) * zoom;
				if (var17 / depth > Rasterizer3D_clipNegativeMidX)
				{
					int ry = pitchCos * y - var11 * pitchSin >> 16;
					int yheight = pitchSin * xzMag >> 16;
					int ybottom = (pitchCos * bottomY >> 16) + yheight; // use bottom height instead of y pos for height
					int var20 = (ry + ybottom) * zoom;
					if (var20 / depth > Rasterizer3D_clipNegativeMidY)
					{
						int ytop = (pitchCos * modelHeight >> 16) + yheight;
						int var22 = (ry - ytop) * zoom;
						return var22 / depth < Rasterizer3D_clipMidY2;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Draw a renderable in the scene
	 *
	 * @param renderable
	 * @param orientation
	 * @param pitchSin
	 * @param pitchCos
	 * @param yawSin
	 * @param yawCos
	 * @param x
	 * @param y
	 * @param z
	 * @param hash
	 */
	@Override
	public void draw(Renderable renderable, int orientation, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z, long hash)
	{
		Model model, offsetModel;
		if (renderable instanceof Model)
		{
			model = (Model) renderable;
			offsetModel = model.getUnskewedModel();
			if (offsetModel == null)
			{
				offsetModel = model;
			}
		}
		else
		{
			model = renderable.getModel();
			if (model == null)
			{
				return;
			}
			offsetModel = model;
		}

		if (computeMode == ComputeMode.NONE)
		{
			// Apply height to renderable from the model
			if (model != renderable)
			{
				renderable.setModelHeight(model.getModelHeight());
			}

			if (!isVisible(model, pitchSin, pitchCos, yawSin, yawCos, x, y, z))
			{
				return;
			}

			client.checkClickbox(model, orientation, pitchSin, pitchCos, yawSin, yawCos, x, y, z, hash);

			targetBufferOffset += sceneUploader.pushSortedModel(
				model, orientation,
				pitchSin, pitchCos,
				yawSin, yawCos,
				x, y, z,
				vertexBuffer, uvBuffer);
		}
		// Model may be in the scene buffer
		else if (offsetModel.getSceneId() == sceneId)
		{
			assert model == renderable;

			if (!isVisible(model, pitchSin, pitchCos, yawSin, yawCos, x, y, z))
			{
				return;
			}

			client.checkClickbox(model, orientation, pitchSin, pitchCos, yawSin, yawCos, x, y, z, hash);

			int tc = Math.min(MAX_TRIANGLE, offsetModel.getFaceCount());
			int uvOffset = offsetModel.getUvBufferOffset();
			int plane = (int) ((hash >> 49) & 3);
			boolean hillskew = offsetModel != model;

			com.vr.GpuIntBuffer b = bufferForTriangles(tc);

			b.ensureCapacity(8);
			IntBuffer buffer = b.getBuffer();
			buffer.put(offsetModel.getBufferOffset());
			buffer.put(uvOffset);
			buffer.put(tc);
			buffer.put(targetBufferOffset);
			buffer.put(FLAG_SCENE_BUFFER | (hillskew ? (1 << 26) : 0) | (plane << 24) | (model.getRadius() << 12) | orientation);
			buffer.put(x + client.getCameraX2()).put(y + client.getCameraY2()).put(z + client.getCameraZ2());

			targetBufferOffset += tc * 3;
		}
		else
		{
			// Temporary model (animated or otherwise not a static Model on the scene)

			// Apply height to renderable from the model
			if (model != renderable)
			{
				renderable.setModelHeight(model.getModelHeight());
			}

			if (!isVisible(model, pitchSin, pitchCos, yawSin, yawCos, x, y, z))
			{
				return;
			}

			client.checkClickbox(model, orientation, pitchSin, pitchCos, yawSin, yawCos, x, y, z, hash);

			boolean hasUv = model.getFaceTextures() != null;

			int len = sceneUploader.pushModel(model, vertexBuffer, uvBuffer);

			com.vr.GpuIntBuffer b = bufferForTriangles(len / 3);

			b.ensureCapacity(8);
			IntBuffer buffer = b.getBuffer();
			buffer.put(tempOffset);
			buffer.put(hasUv ? tempUvOffset : -1);
			buffer.put(len / 3);
			buffer.put(targetBufferOffset);
			buffer.put((model.getRadius() << 12) | orientation);
			buffer.put(x + client.getCameraX2()).put(y + client.getCameraY2()).put(z + client.getCameraZ2());

			tempOffset += len;
			if (hasUv)
			{
				tempUvOffset += len;
			}

			targetBufferOffset += len;
		}
	}

	/**
	 * returns the correct buffer based on triangle count and updates model count
	 *
	 * @param triangles
	 * @return
	 */
	private com.vr.GpuIntBuffer bufferForTriangles(int triangles)
	{
		if (triangles <= SMALL_TRIANGLE_COUNT)
		{
			++smallModels;
			return modelBufferSmall;
		}
		else
		{
			++largeModels;
			return modelBuffer;
		}
	}

	private int getScaledValue(final double scale, final int value)
	{
		return (int) (value * scale + .5);
	}

	private void glDpiAwareViewport(final int x, final int y, final int width, final int height)
	{
		if (OSType.getOSType() == OSType.MacOS)
		{
			// macos handles DPI scaling for us already
			GL43C.glViewport(x, y, width, height);
		}
		else
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			GL43C.glViewport(
				getScaledValue(t.getScaleX(), x),
				getScaledValue(t.getScaleY(), y),
				getScaledValue(t.getScaleX(), width),
				getScaledValue(t.getScaleY(), height));
		}
	}

	private int getDrawDistance()
	{
		final int limit = computeMode != ComputeMode.NONE ? MAX_DISTANCE : DEFAULT_DISTANCE;
		return Ints.constrainToRange(config.drawDistance(), 0, limit);
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
			recreateCLBuffer(glBuffer, clFlags);
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

	private void recreateCLBuffer(com.vr.GLBuffer glBuffer, long clFlags)
	{
		if (computeMode == ComputeMode.OPENCL)
		{
			if (glBuffer.clBuffer != -1)
			{
				CL10.clReleaseMemObject(glBuffer.clBuffer);
			}
			if (glBuffer.size == 0)
			{
				glBuffer.clBuffer = -1;
			}
			else
			{
				glBuffer.clBuffer = CL10GL.clCreateFromGLBuffer(openCLManager.context, clFlags, glBuffer.glBufferId, (int[]) null);
			}
		}
	}

	private void checkGLErrors()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}

		for (; ; )
		{
			int err = GL43C.glGetError();
			if (err == GL43C.GL_NO_ERROR)
			{
				return;
			}

			String errStr;
			switch (err)
			{
				case GL43C.GL_INVALID_ENUM:
					errStr = "INVALID_ENUM";
					break;
				case GL43C.GL_INVALID_VALUE:
					errStr = "INVALID_VALUE";
					break;
				case GL43C.GL_INVALID_OPERATION:
					errStr = "INVALID_OPERATION";
					break;
				case GL43C.GL_INVALID_FRAMEBUFFER_OPERATION:
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
