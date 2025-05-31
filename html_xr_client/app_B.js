// app.js

import RFB from './js/rfb.js';
import KeyTable from './js/input/keysym.js';

window.IS_ANDROID_APP = false; // Will be set by Android

// Global Three.js variables
let scene, camera, renderer;
let skySphere, vncScreenFlat, vncScreenCurved;
let vncTexture;
let screenMaterial;

// VNC state
let rfb;
let dummyTargetForRfb;
let preferredResolution = { width: 0, height: 0, auto: true };

// Screen properties
let SCREEN_DISTANCE = 3;
let SCREEN_WIDTH_WORLD = 3.2;
let SCREEN_HEIGHT_WORLD = 1.8;
const MIN_SCREEN_DISTANCE = 0.1;
const MAX_SCREEN_DISTANCE = 50;
let effectiveScreenDistance = SCREEN_DISTANCE;
let currentCylinderThetaLength = Math.PI / 2.5; // Default, will be updated

let currentScreenType = 'flat';
let currentVncScreenObject = null;

// Camera control variables
const baseCameraFOV = 55;
const minFlattenFOV = 5;
let currentPanMode = 'xy-pan';

const imuEuler = new THREE.Euler(0, 0, 0, 'YXZ');
const manualEuler = new THREE.Euler(0, 0, 0, 'YXZ'); // For 'rotate' pan mode
const cameraPanOffset = new THREE.Vector3(0, 0, 0); // For 'xy-pan' on flat screen
const targetCylindricalPan = { angle: 0, height: 0 }; // For 'xy-pan' on curved screens

let imuEnabled = false;
let isViewPannning = false;
let previousPanPosition = { x: 0, y: 0 };

const PAN_SENSITIVITY_XY_LINEAR = 0.001; // For flat screen pan and curved screen vertical pan
const PAN_SENSITIVITY_XY_ANGULAR = 0.001; // For curved screen angular pan (radians per pixel * distance factor)
const PAN_SENSITIVITY_ROTATE = 0.0025;
const ZOOM_SENSITIVITY = 0.1;

const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();

const uiContainer = document.getElementById('container');
const controlsContainer = document.getElementById('controlsContainer');
const settingsPane = document.getElementById('settingsPane');
const activeControlsPane = document.getElementById('activeControlsPane');
const controlsToggle = document.getElementById('controlsToggle');

const connectButton = document.getElementById('connectButton');
const disconnectButton = document.getElementById('disconnectButton');
const vncHostInput = document.getElementById('vncHost');
const vncPortInput = document.getElementById('vncPort');
const vncPasswordInput = document.getElementById('vncPassword');
const vncResolutionInput = document.getElementById('vncResolution');
const screenTypeSelect = document.getElementById('screenType');
const curvatureControlGroup = document.getElementById('curvatureControlGroup');
const curvatureSlider = document.getElementById('curvatureSlider');
const curvatureValueSpan = document.getElementById('curvatureValue');

const connectStatusDiv = document.getElementById('connectStatus');
const fullscreenButton = document.getElementById('fullscreenButton');
const permissionButton = document.getElementById('permissionButton');

const LS_KEY_HOST = 'vncViewerHost';
const LS_KEY_PORT = 'vncViewerPort';
const LS_KEY_RESOLUTION = 'vncViewerResolution';
const LS_KEY_SCREEN_TYPE = 'vncViewerScreenType';
const LS_KEY_CURVATURE = 'vncViewerCurvature';
const LS_KEY_SCREEN_DISTANCE = 'vncViewerScreenDistance';
const LS_KEY_PAN_OFFSET_X = 'vncViewerPanOffsetX';
const LS_KEY_PAN_OFFSET_Y = 'vncViewerPanOffsetY';
const LS_KEY_CYL_PAN_ANGLE = 'vncViewerCylPanAngle';
const LS_KEY_CYL_PAN_HEIGHT = 'vncViewerCylPanHeight';
const LS_KEY_MANUAL_EULER_X = 'vncViewerManualEulerX';
const LS_KEY_MANUAL_EULER_Y = 'vncViewerManualEulerY';


function initThreeJS() {
    scene = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(baseCameraFOV, window.innerWidth / window.innerHeight, 0.01, 1000);
    camera.position.set(0, 0, 0);

    renderer = new THREE.WebGLRenderer({
        antialias: true,
        precision: 'highp'
    });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.domElement.style.outline = 'none';
    renderer.domElement.setAttribute('tabindex', '0');
    uiContainer.appendChild(renderer.domElement);

    const skyGeometry = new THREE.SphereGeometry(400, 60, 40);
    skyGeometry.scale(-1, 1, 1);
    const skyMaterial = new THREE.MeshBasicMaterial({ color: 0x1a2028 });
    skySphere = new THREE.Mesh(skyGeometry, skyMaterial);
    scene.add(skySphere);

    const placeholderCanvas = document.createElement('canvas');
    placeholderCanvas.width = 256; placeholderCanvas.height = 144;
    const ctx = placeholderCanvas.getContext('2d');
    ctx.fillStyle = '#0a0a0a'; ctx.fillRect(0, 0, 256, 144);
    ctx.fillStyle = '#999999'; ctx.font = 'bold 18px Arial'; ctx.textAlign = 'center';
    ctx.fillText('VNC Not Connected', 128, 72);

    vncTexture = new THREE.CanvasTexture(placeholderCanvas);
    vncTexture.minFilter = THREE.LinearFilter;
    vncTexture.magFilter = THREE.NearestFilter;
    vncTexture.generateMipmaps = false;
    if (renderer.capabilities.getMaxAnisotropy) {
        vncTexture.anisotropy = renderer.capabilities.getMaxAnisotropy();
    }

    screenMaterial = new THREE.MeshBasicMaterial({ map: vncTexture, side: THREE.DoubleSide });

    const flatGeometry = new THREE.PlaneGeometry(SCREEN_WIDTH_WORLD, SCREEN_HEIGHT_WORLD);
    vncScreenFlat = new THREE.Mesh(flatGeometry, screenMaterial);
    scene.add(vncScreenFlat);
    currentVncScreenObject = vncScreenFlat;

    vncScreenCurved = new THREE.Mesh(new THREE.BufferGeometry(), screenMaterial);
    vncScreenCurved.rotation.y = Math.PI;
    vncScreenCurved.scale.x = -1;
    scene.add(vncScreenCurved);
    vncScreenCurved.visible = false;

    updateScreenObjectPositions();

    const ambientLight = new THREE.AmbientLight(0xffffff, 1.2);
    scene.add(ambientLight);

    loadSettings();

    // Android specific.
    if (window.IS_ANDROID_APP) {
        if (controlsContainer) controlsContainer.style.display = 'none';
        if (controlsToggle) controlsToggle.style.display = 'none';
        if (fullscreenButton) fullscreenButton.style.display = 'none';
        // The permission button for browser motion API might also be hidden if Viture is primary
        if (permissionButton) permissionButton.style.display = 'none'; // Or controlled by setVitureIMU
        // No need to call setupUIToggle() or setControlsVisibility() if IS_ANDROID_APP is true
    }

    screenTypeSelect.dispatchEvent(new Event('change'));
    curvatureSlider.dispatchEvent(new Event('input'));

    window.addEventListener('resize', onWindowResize, false);

    addInteractionControls();
    setupUIToggle();
}

function updateScreenGeometryAndInitialDistance(vncWidth, vncHeight) {
    if (!vncWidth || !vncHeight || vncWidth <= 0 || vncHeight <= 0) {
        vncWidth = 800; vncHeight = 600;
    }

    const vFov = THREE.MathUtils.degToRad(baseCameraFOV);
    SCREEN_HEIGHT_WORLD = 2.0;
    SCREEN_WIDTH_WORLD = (vncWidth / vncHeight) * SCREEN_HEIGHT_WORLD;

    const targetAngularHeightRatio = 0.7;
    const targetAngularHeight = vFov * targetAngularHeightRatio;
    let newScreenDistance = (SCREEN_HEIGHT_WORLD / 2) / Math.tan(targetAngularHeight / 2);

    if (localStorage.getItem(LS_KEY_SCREEN_DISTANCE) === null) {
         SCREEN_DISTANCE = Math.max(MIN_SCREEN_DISTANCE, Math.min(MAX_SCREEN_DISTANCE, newScreenDistance));
    }
    if (isNaN(SCREEN_DISTANCE)) SCREEN_DISTANCE = 3;

    if (vncScreenFlat.geometry) vncScreenFlat.geometry.dispose();
    vncScreenFlat.geometry = new THREE.PlaneGeometry(SCREEN_WIDTH_WORLD, SCREEN_HEIGHT_WORLD);

    updateCameraProjectionAndScreenDistance();
    console.log(`Screen Geo & Initial Dist Updated: VNC ${vncWidth}x${vncHeight} | World ${SCREEN_WIDTH_WORLD.toFixed(2)}x${SCREEN_HEIGHT_WORLD.toFixed(2)} | BaseDist ${SCREEN_DISTANCE.toFixed(2)}`);
}

function updateCameraProjectionAndScreenDistance() {
    if (!camera) return;

    if (currentScreenType === 'flattened-curved') {
        const lerpFactor = parseFloat(curvatureSlider.value) / 100.0;
        camera.fov = THREE.MathUtils.lerp(minFlattenFOV, baseCameraFOV, lerpFactor);

        const baseFOVRads = THREE.MathUtils.degToRad(baseCameraFOV);
        const currentFOVRads = THREE.MathUtils.degToRad(camera.fov);

        if (Math.tan(currentFOVRads / 2) > 0.0001 && Math.tan(baseFOVRads / 2) > 0.0001) {
            effectiveScreenDistance = SCREEN_DISTANCE * (Math.tan(baseFOVRads / 2) / Math.tan(currentFOVRads / 2));
        } else {
            effectiveScreenDistance = SCREEN_DISTANCE;
        }
        effectiveScreenDistance = Math.max(MIN_SCREEN_DISTANCE, Math.min(MAX_SCREEN_DISTANCE * 10, effectiveScreenDistance));
    } else {
        camera.fov = baseCameraFOV;
        effectiveScreenDistance = SCREEN_DISTANCE;
    }
    camera.updateProjectionMatrix();
    updateScreenObjectPositions();
}

function updateScreenObjectPositions() {
    if (!vncScreenFlat || !vncScreenCurved || !camera ) return;

    vncScreenFlat.position.set(0, 0, -effectiveScreenDistance);

    const curveRadius = effectiveScreenDistance * 0.95;
    let curveAngle = SCREEN_WIDTH_WORLD / curveRadius;
    if (curveAngle <= 0 || isNaN(curveAngle) || curveRadius <=0) {
        curveAngle = Math.PI / 3;
    } else {
        curveAngle = Math.min(curveAngle, Math.PI * 1.5);
    }
    currentCylinderThetaLength = curveAngle; // Store for panning limits

    const radialSegments = Math.max(32, Math.floor(SCREEN_WIDTH_WORLD * 10));

    if(vncScreenCurved.geometry) vncScreenCurved.geometry.dispose();
    vncScreenCurved.geometry = new THREE.CylinderGeometry(
        Math.max(0.01, curveRadius),
        Math.max(0.01, curveRadius),
        SCREEN_HEIGHT_WORLD,
        radialSegments,
        1, true,
        -curveAngle / 2, curveAngle
    );
    vncScreenCurved.position.set(0, 0, -effectiveScreenDistance + curveRadius - 0.001);
}

function onWindowResize() {
    if (!camera || !renderer) return;
    camera.aspect = window.innerWidth / window.innerHeight;
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(window.innerWidth, window.innerHeight);

    if (rfb && rfb._fbWidth && rfb._fbHeight) {
         updateScreenGeometryAndInitialDistance(rfb._fbWidth, rfb._fbHeight);
    } else {
        updateCameraProjectionAndScreenDistance();
    }
}

function updateCameraOrientation() {
    if (!camera) return;

    const finalPosition = new THREE.Vector3();
    const finalQuaternion = new THREE.Quaternion();
    const baseViewQuaternion = new THREE.Quaternion();
    const _imuQuaternion = new THREE.Quaternion().setFromEuler(imuEuler);

    if (currentPanMode === 'xy-pan') {
        if (currentVncScreenObject === vncScreenFlat || !currentVncScreenObject) { // Default to flat-like pan if no object
            finalPosition.set(cameraPanOffset.x, cameraPanOffset.y, 0);
            const lookAtTarget = new THREE.Vector3(cameraPanOffset.x, cameraPanOffset.y, -1); // Look straight ahead
            const tempMatrix = new THREE.Matrix4().lookAt(finalPosition, lookAtTarget, camera.up);
            baseViewQuaternion.setFromRotationMatrix(tempMatrix);
        } else { // Curved or FlattenedCurved (currentVncScreenObject === vncScreenCurved)
            const curveRadius = effectiveScreenDistance * 0.95;
            const panAngle = targetCylindricalPan.angle;
            const panHeight = targetCylindricalPan.height;

            const targetSurfacePoint = new THREE.Vector3();
            targetSurfacePoint.x = curveRadius * Math.sin(panAngle);
            targetSurfacePoint.y = panHeight;
            const cylinderAxisZ = -effectiveScreenDistance + curveRadius;
            targetSurfacePoint.z = cylinderAxisZ - curveRadius * Math.cos(panAngle);

            // Normal from surface pointing towards where camera should be (radially 'outward' from cylinder view, but 'inward' towards cylinder axis)
            const normalTowardsCamera = new THREE.Vector3(-Math.sin(panAngle), 0, Math.cos(panAngle));
            // This normal is if the cylinder's "front" (cos=1 part) is along +Z.
            // Our cylinder's front (cos=1 part, angle=0) is along -Z from its axis.
            // So normal from surface towards camera is: (sin(angle_world), 0, cos(angle_world))
            // Where angle_world = panAngle.

            finalPosition.copy(targetSurfacePoint).addScaledVector(normalTowardsCamera, effectiveScreenDistance);

            const tempMatrix = new THREE.Matrix4().lookAt(finalPosition, targetSurfacePoint, camera.up);
            baseViewQuaternion.setFromRotationMatrix(tempMatrix);
        }
    } else { // 'rotate' mode
        finalPosition.set(0,0,0); // Pure rotation around origin
        baseViewQuaternion.setFromEuler(manualEuler);
    }

    if (imuEnabled && (imuEuler.x !== 0 || imuEuler.y !== 0 || imuEuler.z !== 0)) {
        finalQuaternion.multiplyQuaternions(_imuQuaternion, baseViewQuaternion);
    } else {
        finalQuaternion.copy(baseViewQuaternion);
    }

    camera.position.copy(finalPosition);
    camera.quaternion.slerp(finalQuaternion, 0.6);
    camera.updateMatrixWorld(true);
}

// Mobile/Android App specific events and handlers.
//

// Function to be called by Android to initialize VNC connection
window.connectVNCFromAndroid = function(host, port, password, resolution) {
    console.log(`Android attempting to connect: ${host}:${port} res: ${resolution}`);
    if (vncHostInput && vncPortInput && vncPasswordInput && vncResolutionInput) {
        vncHostInput.value = host;
        vncPortInput.value = port;
        vncPasswordInput.value = password;
        vncResolutionInput.value = resolution || 'auto'; // Default to auto if not provided
        connectVNC(); // Call your existing connect function
    } else {
        console.error("VNC input elements not found for Android connection.");
    }
};

// Function for Android to send IMU data from Viture glasses
// Viture SDK: eulerYaw (up-axis), eulerPitch (right-axis) - typically radians
window.setVitureIMU = function(rawYaw, rawPitch) {
    if (!camera || !imuEuler) return;
    imuEnabled = true; // Enable IMU processing

    // Viture SDK Euler: Yaw (up-axis), Pitch (right-axis), Roll (front-axis)
    // Three.js 'YXZ' Euler: .y (Yaw), .x (Pitch), .z (Roll)
    // Assuming rawYaw and rawPitch are in radians.
    // Correct mapping based on typical coordinate systems:
    // Viture Yaw (rotation around Y) -> imuEuler.y
    // Viture Pitch (rotation around X) -> imuEuler.x
    // We need to be careful about the sign and initial orientation.
    // A common setup is device's +Y up, +X right, +Z forward.
    // Glasses +Y up, +X right, +Z out of eyes.
    // Viture yaw is around up-axis, pitch around right-axis.
    // Let's assume direct mapping first, and adjust if inverted.
    // Three.js Euler order 'YXZ':
    //   y: rotation around Y (yaw)
    //   x: rotation around new X (pitch)
    //   z: rotation around new Z (roll)
    imuEuler.set(rawPitch, rawYaw, 0, 'YXZ'); // pitch (X), yaw (Y), roll (Z)

    // Hide permission button if Viture IMU is active
    if (permissionButton) {
        permissionButton.style.display = 'none';
    }
};

const TOUCH_PAN_SENSITIVITY_MULTIPLIER = 2.0; // Adjust as needed for touch
const TOUCH_ZOOM_SENSITIVITY_MULTIPLIER = 1.0; // Adjust as needed for touch

// Function for Android to send touch pan deltas
window.performTouchPan = function(deltaX, deltaY) {
    if (!camera) return;
    // We reuse existing panning logic but need to simulate the conditions.
    // The original code uses mousemove event.clientX/Y. We have deltas.

    const activePanMode = currentPanMode; // Use the currently selected pan mode

    if (activePanMode === 'xy-pan') {
        if (currentVncScreenObject === vncScreenFlat || !currentVncScreenObject) {
            const panFactor = effectiveScreenDistance * PAN_SENSITIVITY_XY_LINEAR * TOUCH_PAN_SENSITIVITY_MULTIPLIER;
            cameraPanOffset.x -= deltaX * panFactor;
            cameraPanOffset.y += deltaY * panFactor;
        } else { // Curved or FlattenedCurved
            const curveRadius = effectiveScreenDistance * 0.95;
            if (curveRadius > 0.01) {
                targetCylindricalPan.angle -= (deltaX * PAN_SENSITIVITY_XY_ANGULAR * effectiveScreenDistance * TOUCH_PAN_SENSITIVITY_MULTIPLIER) / curveRadius;
                targetCylindricalPan.height += deltaY * PAN_SENSITIVITY_XY_LINEAR * effectiveScreenDistance * TOUCH_PAN_SENSITIVITY_MULTIPLIER;

                const maxAngle = currentCylinderThetaLength / 2;
                targetCylindricalPan.angle = Math.max(-maxAngle, Math.min(maxAngle, targetCylindricalPan.angle));
                const maxHeight = SCREEN_HEIGHT_WORLD / 2;
                targetCylindricalPan.height = Math.max(-maxHeight, Math.min(maxHeight, targetCylindricalPan.height));
            }
        }
    } else { // 'rotate' mode
        manualEuler.y -= deltaX * PAN_SENSITIVITY_ROTATE * TOUCH_PAN_SENSITIVITY_MULTIPLIER;
        manualEuler.x -= deltaY * PAN_SENSITIVITY_ROTATE * TOUCH_PAN_SENSITIVITY_MULTIPLIER;
        manualEuler.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, manualEuler.x));
    }
    saveSettings();
};

// Function for Android to send touch zoom factor
window.performTouchZoom = function(scaleFactor) {
    if (!camera) return;
    // scaleFactor > 1 means zoom in, < 1 means zoom out
    const zoomAmount = (scaleFactor - 1.0) * ZOOM_SENSITIVITY * TOUCH_ZOOM_SENSITIVITY_MULTIPLIER * 10; // Make it more like scroll
    const zoomFactorNormalized = 1.0 - zoomAmount;

    SCREEN_DISTANCE /= zoomFactorNormalized; // Original logic was /= (1 - deltaY * small_val)
                                          // if scaleFactor from Android is >1 for pinch out (zoom in),
                                          // then to zoom in (decrease distance), we need to divide by it.
                                          // If scaleFactor <1 for pinch in (zoom out), dividing by it increases distance.
                                          // This seems correct with how ScaleGestureDetector usually provides scaleFactor.

    SCREEN_DISTANCE = Math.max(MIN_SCREEN_DISTANCE, Math.min(MAX_SCREEN_DISTANCE, SCREEN_DISTANCE));
    updateCameraProjectionAndScreenDistance();
    saveSettings();
};

function updateVNCCursor() {
    if (rfb && rfb._canvas && renderer.domElement) {
        if (renderer.domElement.style.cursor !== rfb._canvas.style.cursor) {
            renderer.domElement.style.cursor = rfb._canvas.style.cursor;
        }
    } else if (renderer.domElement) {
        renderer.domElement.style.cursor = 'default';
    }
}

function animate() {
    requestAnimationFrame(animate);
    if(!scene || !camera || !renderer) return;
    updateCameraOrientation();
    if (rfb && rfb._canvas) {
        if (vncTexture.image !== rfb._canvas) vncTexture.image = rfb._canvas;
        if (rfb._canvas.width > 0 && rfb._canvas.height > 0 ) {
            const display = rfb.get_display ? rfb.get_display() : null;
            if (display && display.pending()) vncTexture.needsUpdate = true;
            else if (!display) vncTexture.needsUpdate = true;
        }
    }
    updateVNCCursor();
    renderer.render(scene, camera);
}

function connectVNC() {
    const host = vncHostInput.value;
    const port = vncPortInput.value;
    const password = vncPasswordInput.value;
    const resValue = vncResolutionInput.value.toLowerCase().trim();

    if (resValue === 'auto') {
        preferredResolution.auto = true;
    } else {
        const parts = resValue.split('x');
        if (parts.length === 2) {
            const w = parseInt(parts[0], 10); const h = parseInt(parts[1], 10);
            if (w > 0 && h > 0) {
                preferredResolution.width = w; preferredResolution.height = h; preferredResolution.auto = false;
            } else {
                alert("Invalid resolution. Use 'auto' or 'WIDTHxHEIGHT'. Defaulting to auto.");
                preferredResolution.auto = true; vncResolutionInput.value = "auto";
            }
        } else {
            alert("Invalid resolution. Use 'auto' or 'WIDTHxHEIGHT'. Defaulting to auto.");
            preferredResolution.auto = true; vncResolutionInput.value = "auto";
        }
    }
    localStorage.setItem(LS_KEY_HOST, vncHostInput.value);
    localStorage.setItem(LS_KEY_PORT, vncPortInput.value);
    localStorage.setItem(LS_KEY_RESOLUTION, vncResolutionInput.value);

    if (rfb) disconnectVNC();
    connectStatusDiv.textContent = `Connecting to ws://${host}:${port}...`;
    setControlsVisibility(false);

    if (!dummyTargetForRfb) {
        dummyTargetForRfb = document.createElement('div');
        dummyTargetForRfb.id = "noVNC_hidden_target"; dummyTargetForRfb.style.display = 'none';
        document.body.appendChild(dummyTargetForRfb);
    }
    const websocketUrl = `ws://${host}:${port}`;
    try {
        rfb = new RFB(dummyTargetForRfb, websocketUrl, { credentials: { password: password }, shared: true });
        rfb.scaleViewport = false; rfb.clipViewport = false; rfb.resizeSession = !preferredResolution.auto;
    } catch (e) {
        connectStatusDiv.textContent = `Error initializing RFB: ${e.message}`;
        console.error("RFB instantiation error:", e);
        setControlsVisibility(true); return;
    }
    rfb.addEventListener('connect', () => {
        connectStatusDiv.textContent = `Connected! (Server: ${rfb._fbName || '...'})`;
        settingsPane.classList.add('hidden'); activeControlsPane.classList.remove('hidden');
        if (rfb._canvas) { vncTexture.image = rfb._canvas; vncTexture.needsUpdate = true; }
        else { console.error("VNC Connect: rfb._canvas NOT found!"); }
        setTimeout(() => {
            if (rfb && rfb._fbWidth && rfb._fbHeight) {
                let currentFbWidth = rfb._fbWidth; let currentFbHeight = rfb._fbHeight;
                if (!preferredResolution.auto && rfb.resizeSession && rfb._supportsSetDesktopSize &&
                    (preferredResolution.width !== currentFbWidth || preferredResolution.height !== currentFbHeight)) {
                    RFB.messages.setDesktopSize(rfb._sock, preferredResolution.width, preferredResolution.height, rfb._screenID, rfb._screenFlags);
                } else { updateScreenGeometryAndInitialDistance(currentFbWidth, currentFbHeight); }
            } else { updateScreenGeometryAndInitialDistance(800, 600); }
        }, 300);
        renderer.domElement.focus();
    });
    rfb.addEventListener('desktopsize', (event) => {
        let w, h;
        if (event.detail && event.detail.width && event.detail.height) { w = event.detail.width; h = event.detail.height; }
        else if (rfb && rfb._fbWidth && rfb._fbHeight) { w = rfb._fbWidth; h = rfb._fbHeight; }
        if (w && h) updateScreenGeometryAndInitialDistance(w, h);
    });
    rfb.addEventListener('disconnect', (event) => {
        const clean = event.detail && event.detail.clean;

        connectStatusDiv.textContent = `Disconnected. ${clean ? "Cleanly." : "Unexpectedly."}`;
        setControlsVisibility(true); settingsPane.classList.remove('hidden'); activeControlsPane.classList.add('hidden');
        const placeholderCanvas = document.createElement('canvas');
        placeholderCanvas.width = 256; placeholderCanvas.height = 144;
        const ctx = placeholderCanvas.getContext('2d');
        ctx.fillStyle = '#0a0a0a'; ctx.fillRect(0, 0, 256, 144);
        ctx.fillStyle = '#999999'; ctx.font = 'bold 18px Arial'; ctx.textAlign = 'center';
        ctx.fillText('Disconnected', 128, 72);
        if (vncTexture) { vncTexture.image = placeholderCanvas; vncTexture.needsUpdate = true; }
        rfb = null;
    });
    rfb.addEventListener('credentialsrequired', () => {
        const pass = prompt("Password required (leave blank if none):");
        rfb.sendCredentials({ password: pass || "" });
    });
    rfb.addEventListener('desktopname', (event) => {
        if (rfb && rfb._rfbConnectionState === 'connected') {
             connectStatusDiv.textContent = `Connected! (Server: ${event.detail.name})`;
        }
    });
}

function disconnectVNC() { if (rfb) rfb.disconnect(); }

function requestMotionPermission() {
    // These are for browser's DeviceOrientationEvent, Android will drive these if available.
    if (window.IS_ANDROID_APP) {
        console.log("Motion permission handled by Android App.");
        if(permissionButton) permissionButton.style.display = 'none';
        return;
    }

    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        DeviceOrientationEvent.requestPermission()
            .then(permissionState => {
                if (permissionState === 'granted') {
                    window.addEventListener('deviceorientation', handleOrientation, true);
                    imuEnabled = true; permissionButton.textContent = "Motion Tracking Active"; permissionButton.disabled = true;
                } else {
                    imuEnabled = false; alert('Permission for motion tracking was denied.'); permissionButton.textContent = "Permission Denied";
                }
            }).catch(error => {
                imuEnabled = false; console.error('DeviceOrientationEvent.requestPermission error:', error);
                alert('Error requesting motion permission.'); permissionButton.textContent = "Motion Permission Error";
            });
    } else if (typeof DeviceOrientationEvent !== 'undefined') {
        window.addEventListener('deviceorientation', handleOrientation, true);
        imuEnabled = true; permissionButton.textContent = "Motion Tracking Active (auto)"; permissionButton.disabled = true;
    } else {
        imuEnabled = false; alert('Device orientation not supported.');
        permissionButton.textContent = "Motion API Not Supported"; permissionButton.disabled = true;
    }
}

const degToRad = THREE.MathUtils.degToRad;
function handleOrientation(event) {
    if (window.IS_ANDROID_APP) return; // From android app.

    if (!event.alpha && !event.beta && !event.gamma) return;
    if (!imuEnabled) return;
    imuEuler.set(degToRad(event.beta), degToRad(event.alpha), -degToRad(event.gamma), 'YXZ');
}

function addInteractionControls() {
    renderer.domElement.addEventListener('mousedown', (event) => {
        if (event.shiftKey && event.altKey && event.metaKey) {
            isViewPannning = true;
            previousPanPosition.x = event.clientX; previousPanPosition.y = event.clientY;
            uiContainer.classList.add('dragging'); event.preventDefault();
        } else {
            let freelookPan = false;
            if (!rfb && event.buttons === 1 && !event.shiftKey && !event.altKey && !event.metaKey) {
                isViewPannning = true; freelookPan = true;
                previousPanPosition.x = event.clientX; previousPanPosition.y = event.clientY;
            }
            if (!freelookPan && (!rfb || !currentVncScreenObject)) return;

            if (!freelookPan) {
                if (document.activeElement !== renderer.domElement) renderer.domElement.focus();
                handleVNCMouseEvent(event, 'down');
            }
        }
    });

    window.addEventListener('mousemove', (event) => {
        if (isViewPannning) {
            const deltaX = event.clientX - previousPanPosition.x;
            const deltaY = event.clientY - previousPanPosition.y;
            previousPanPosition.x = event.clientX; previousPanPosition.y = event.clientY;

            let activePanMode = currentPanMode;
            // Force 'rotate' mode for freelook (no VNC, no modifiers, left button down)
            if (!rfb && !event.shiftKey && !event.altKey && !event.metaKey && (event.buttons === 1 || (event.type === 'mousemove' && isViewPannning))) {
                 // Check isViewPannning for mousemove ensures it was initiated by a freelook mousedown
                 if(document.pointerLockElement === renderer.domElement || event.buttons === 1) { // also check pointer lock
                    activePanMode = 'rotate';
                 }
            }

            if (activePanMode === 'xy-pan') {
                if (currentVncScreenObject === vncScreenFlat || !currentVncScreenObject) {
                    const panFactor = effectiveScreenDistance * PAN_SENSITIVITY_XY_LINEAR;
                    cameraPanOffset.x -= deltaX * panFactor; // Correct direction based on typical screen coords
                    cameraPanOffset.y += deltaY * panFactor;
                } else { // Curved or FlattenedCurved
                    const curveRadius = effectiveScreenDistance * 0.95;
                    if (curveRadius > 0.01) { // Avoid division by zero or tiny radius issues
                        // Angular pan (horizontal)
                        // Convert pixel delta to an angle change. Sensitivity might need tuning.
                        // A larger radius means a given pixel delta should correspond to a smaller angle.
                        targetCylindricalPan.angle -= (deltaX * PAN_SENSITIVITY_XY_ANGULAR * effectiveScreenDistance) / curveRadius;

                        // Linear pan (vertical)
                        // Convert pixel delta to world units. Sensitivity scales with distance.
                        targetCylindricalPan.height += deltaY * PAN_SENSITIVITY_XY_LINEAR * effectiveScreenDistance;

                        // Clamp values
                        const maxAngle = currentCylinderThetaLength / 2;
                        targetCylindricalPan.angle = Math.max(-maxAngle, Math.min(maxAngle, targetCylindricalPan.angle));
                        const maxHeight = SCREEN_HEIGHT_WORLD / 2;
                        targetCylindricalPan.height = Math.max(-maxHeight, Math.min(maxHeight, targetCylindricalPan.height));
                    }
                }
            } else { // 'rotate' mode
                manualEuler.y -= deltaX * PAN_SENSITIVITY_ROTATE;
                manualEuler.x -= deltaY * PAN_SENSITIVITY_ROTATE;
                manualEuler.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, manualEuler.x));
            }
            saveSettings(); // Save relevant pan/rotation state
        } else {
            if (!rfb || !currentVncScreenObject) return;
            handleVNCMouseEvent(event, 'move');
        }
    });

    window.addEventListener('mouseup', (event) => {
        if (isViewPannning) {
            isViewPannning = false; uiContainer.classList.remove('dragging');
            if(document.pointerLockElement === renderer.domElement) document.exitPointerLock();
        }
        if (rfb && currentVncScreenObject && !(event.shiftKey && event.altKey && event.metaKey)) {
            handleVNCMouseEvent(event, 'up');
        }
    });

    document.addEventListener('mouseleave', () => {
        if (isViewPannning) { isViewPannning = false; uiContainer.classList.remove('dragging'); }
    });

    renderer.domElement.addEventListener('wheel', (event) => {
        if (event.shiftKey && event.altKey && event.metaKey) {
            event.preventDefault();
            const zoomFactor = 1.0 - (event.deltaY * ZOOM_SENSITIVITY * 0.01);
            SCREEN_DISTANCE /= zoomFactor;
            SCREEN_DISTANCE = Math.max(MIN_SCREEN_DISTANCE, Math.min(MAX_SCREEN_DISTANCE, SCREEN_DISTANCE));
            updateCameraProjectionAndScreenDistance();
            saveSettings();
        }
    }, { passive: false });

    renderer.domElement.addEventListener('contextmenu', (event) => {
        if (isViewPannning) { event.preventDefault(); return; }
        if (rfb && currentVncScreenObject) {
            const rect = renderer.domElement.getBoundingClientRect();
            mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
            mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
            raycaster.setFromCamera(mouse, camera);
            const intersects = raycaster.intersectObject(currentVncScreenObject, true);
            if (intersects.length > 0) event.preventDefault();
        }
    });
    renderer.domElement.addEventListener('keydown', (event) => {
        if (isViewPannning || !rfb) return;
        const code = event.code; let keysym = KeyTable[code];
        if (KeyTable.hasOwnProperty(code) || (event.key.length === 1 && !event.ctrlKey && !event.altKey && !event.metaKey) ||
            ["Tab", "Enter", "Escape", "Backspace", "Delete"].includes(event.key) ||
            (event.key.startsWith("Arrow") && !event.shiftKey && !event.ctrlKey && !event.altKey && !event.metaKey)
        ) { event.preventDefault(); }

        if (keysym === undefined) {
            if (event.key.length === 1) keysym = event.key.charCodeAt(0);
            else {
                switch (event.key) {
                    case 'Escape': keysym = KeyTable.XK_Escape; break; case 'Tab': keysym = KeyTable.XK_Tab; break;
                    case 'Backspace': keysym = KeyTable.XK_BackSpace; break; case 'Enter': keysym = KeyTable.XK_Return; break;
                    case 'Delete': keysym = KeyTable.XK_Delete; break;
                    case 'Shift': keysym = (code === "ShiftLeft") ? KeyTable.XK_Shift_L : KeyTable.XK_Shift_R; break;
                    case 'Control': keysym = (code === "ControlLeft") ? KeyTable.XK_Control_L : KeyTable.XK_Control_R; break;
                    case 'Alt': keysym = (code === "AltLeft") ? KeyTable.XK_Alt_L : KeyTable.XK_Alt_R; break;
                    case 'Meta': keysym = (code === "MetaLeft" || code === "OSLeft") ? KeyTable.XK_Meta_L : KeyTable.XK_Meta_R; break;
                    default: console.warn(`Unmapped keydown: key="${event.key}", code="${code}"`); return;
                }
            }
        }
        if (keysym !== undefined) rfb.sendKey(keysym, code, true);
    });
    renderer.domElement.addEventListener('keyup', (event) => {
        if (isViewPannning || !rfb) return;
        const code = event.code; let keysym = KeyTable[code];
        if (keysym === undefined) {
            if (event.key.length === 1) keysym = event.key.charCodeAt(0);
            else {
                 switch (event.key) {
                    case 'Escape': keysym = KeyTable.XK_Escape; break; case 'Tab': keysym = KeyTable.XK_Tab; break;
                    case 'Backspace': keysym = KeyTable.XK_BackSpace; break; case 'Enter': keysym = KeyTable.XK_Return; break;
                    case 'Delete': keysym = KeyTable.XK_Delete; break;
                    case 'Shift': keysym = (code === "ShiftLeft") ? KeyTable.XK_Shift_L : KeyTable.XK_Shift_R; break;
                    case 'Control': keysym = (code === "ControlLeft") ? KeyTable.XK_Control_L : KeyTable.XK_Control_R; break;
                    case 'Alt': keysym = (code === "AltLeft") ? KeyTable.XK_Alt_L : KeyTable.XK_Alt_R; break;
                    case 'Meta': keysym = (code === "MetaLeft" || code === "OSLeft") ? KeyTable.XK_Meta_L : KeyTable.XK_Meta_R; break;
                    default: return;
                }
            }
        }
        if (keysym !== undefined) rfb.sendKey(keysym, code, false);
    });
    // Freelook pointer lock initiation
    renderer.domElement.addEventListener('mousedown', (event) => {
        if (!rfb && event.buttons === 1 && !event.shiftKey && !event.altKey && !event.metaKey) {
            if(renderer.domElement.requestPointerLock) renderer.domElement.requestPointerLock();
        }
    });
}
function handleVNCMouseEvent(event, type) {
    if (!rfb || !currentVncScreenObject || !rfb._canvas || rfb._canvas.width === 0) return;
    const rect = renderer.domElement.getBoundingClientRect();
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);
    const intersects = raycaster.intersectObject(currentVncScreenObject, true);
    if (intersects.length > 0) {
        const uv = intersects[0].uv; if (!uv) return;
        const fbWidth = rfb._fbWidth; const fbHeight = rfb._fbHeight;
        if (!fbWidth || !fbHeight) { console.warn("rfb._fbWidth or rfb._fbHeight not available for VNC mouse event."); return; }
        const vncX = Math.floor(uv.x * fbWidth); const vncY = Math.floor((1.0 - uv.y) * fbHeight);
        let buttonMask = RFB._convertButtonMask(event.buttons);
        if (rfb.sendPointerEvent) rfb.sendPointerEvent(vncX, vncY, buttonMask);
        else if (rfb._sendMouse) rfb._sendMouse(vncX, vncY, buttonMask);
        else console.warn("No method found on RFB to send pointer events.");
    }
}

function setControlsVisibility(showFullPane) {
    if (showFullPane) {
        controlsContainer.classList.remove('hidden');
        controlsToggle.classList.remove('collapsed');
        controlsToggle.innerHTML = '✕'; controlsToggle.title = "Hide Settings";
    } else {
        controlsContainer.classList.add('hidden');
        controlsToggle.classList.add('collapsed');
        controlsToggle.innerHTML = '☰'; controlsToggle.title = "Show Settings";
    }
}
function setupUIToggle() {
    setControlsVisibility(true);
    settingsPane.classList.remove('hidden'); activeControlsPane.classList.add('hidden');
    controlsToggle.addEventListener('click', () => {
        const isHidden = controlsContainer.classList.contains('hidden');
        setControlsVisibility(isHidden);
    });
}
function loadSettings() {
    vncHostInput.value = localStorage.getItem(LS_KEY_HOST) || 'localhost';
    vncPortInput.value = localStorage.getItem(LS_KEY_PORT) || '5901';
    vncResolutionInput.value = localStorage.getItem(LS_KEY_RESOLUTION) || 'auto';

    screenTypeSelect.value = localStorage.getItem(LS_KEY_SCREEN_TYPE) || 'flat';
    curvatureSlider.value = localStorage.getItem(LS_KEY_CURVATURE) || '100';
    curvatureValueSpan.textContent = curvatureSlider.value;

    SCREEN_DISTANCE = parseFloat(localStorage.getItem(LS_KEY_SCREEN_DISTANCE)) || 3.0;

    cameraPanOffset.x = parseFloat(localStorage.getItem(LS_KEY_PAN_OFFSET_X)) || 0;
    cameraPanOffset.y = parseFloat(localStorage.getItem(LS_KEY_PAN_OFFSET_Y)) || 0;

    targetCylindricalPan.angle = parseFloat(localStorage.getItem(LS_KEY_CYL_PAN_ANGLE)) || 0;
    targetCylindricalPan.height = parseFloat(localStorage.getItem(LS_KEY_CYL_PAN_HEIGHT)) || 0;

    manualEuler.x = parseFloat(localStorage.getItem(LS_KEY_MANUAL_EULER_X)) || 0;
    manualEuler.y = parseFloat(localStorage.getItem(LS_KEY_MANUAL_EULER_Y)) || 0;
}
function saveSettings() {
    localStorage.setItem(LS_KEY_SCREEN_TYPE, screenTypeSelect.value);
    localStorage.setItem(LS_KEY_CURVATURE, curvatureSlider.value);
    localStorage.setItem(LS_KEY_SCREEN_DISTANCE, SCREEN_DISTANCE.toString());

    localStorage.setItem(LS_KEY_PAN_OFFSET_X, cameraPanOffset.x.toString());
    localStorage.setItem(LS_KEY_PAN_OFFSET_Y, cameraPanOffset.y.toString());

    localStorage.setItem(LS_KEY_CYL_PAN_ANGLE, targetCylindricalPan.angle.toString());
    localStorage.setItem(LS_KEY_CYL_PAN_HEIGHT, targetCylindricalPan.height.toString());

    localStorage.setItem(LS_KEY_MANUAL_EULER_X, manualEuler.x.toString());
    localStorage.setItem(LS_KEY_MANUAL_EULER_Y, manualEuler.y.toString());
}

connectButton.addEventListener('click', connectVNC);
disconnectButton.addEventListener('click', disconnectVNC);

screenTypeSelect.addEventListener('change', (event) => {
    currentScreenType = event.target.value;
    vncScreenFlat.visible = false; vncScreenCurved.visible = false;
    curvatureControlGroup.classList.add('hidden');

    const oldPanMode = currentPanMode;

    if (currentScreenType === 'flat') {
        vncScreenFlat.visible = true; currentVncScreenObject = vncScreenFlat;
        currentPanMode = 'xy-pan';
    } else if (currentScreenType === 'curved') {
        vncScreenCurved.visible = true; currentVncScreenObject = vncScreenCurved;
        currentPanMode = 'rotate';
    } else if (currentScreenType === 'flattened-curved') {
        vncScreenCurved.visible = true; currentVncScreenObject = vncScreenCurved;
        curvatureControlGroup.classList.remove('hidden');
        currentPanMode = 'xy-pan';
    }

    if (currentPanMode !== oldPanMode) {
        // Reset the state of the pan mode we're switching AWAY from
        if (oldPanMode === 'xy-pan') {
            // cameraPanOffset.set(0,0,0); // Keep for now to allow switching back
            // targetCylindricalPan.angle = 0; targetCylindricalPan.height = 0;
        } else if (oldPanMode === 'rotate') {
            // manualEuler.set(0,0,0); // Keep for now
        }
        // For a cleaner switch, you might uncomment the resets above.
        // For now, let's allow state to persist across mode switches in case user wants to toggle.
        // However, if switching TO xy-pan, rotation should be zeroed for a "straight-on" view.
        // if (currentPanMode === 'xy-pan') manualEuler.set(0,0,0); // Reset view rotation
        // If switching TO rotate, pan offsets should be zeroed.
        // if (currentPanMode === 'rotate') {
        //    cameraPanOffset.set(0,0,0);
        //    targetCylindricalPan.angle = 0; targetCylindricalPan.height = 0;
        // }
    }

    updateCameraProjectionAndScreenDistance();
    saveSettings();
});

curvatureSlider.addEventListener('input', (event) => {
    curvatureValueSpan.textContent = event.target.value;
    updateCameraProjectionAndScreenDistance();
    saveSettings();
});

fullscreenButton.addEventListener('click', () => {
    if (document.fullscreenElement) document.exitFullscreen();
    else uiContainer.requestFullscreen().catch(err => alert(`Fullscreen error: ${err.message}`));
});
permissionButton.addEventListener('click', requestMotionPermission);

// --- Initialization ---
initThreeJS();
animate();
