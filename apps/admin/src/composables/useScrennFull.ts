const methodMap = [
	[
		'requestFullscreen',
		'exitFullscreen',
		'fullscreenElement',
		'fullscreenEnabled',
		'fullscreenchange',
		'fullscreenerror',
	],
	// New WebKit
	[
		'webkitRequestFullscreen',
		'webkitExitFullscreen',
		'webkitFullscreenElement',
		'webkitFullscreenEnabled',
		'webkitfullscreenchange',
		'webkitfullscreenerror',
	],
	// Old WebKit
	[
		'webkitRequestFullScreen',
		'webkitCancelFullScreen',
		'webkitCurrentFullScreenElement',
		'webkitCancelFullScreen',
		'webkitfullscreenchange',
		'webkitfullscreenerror',
	],
	[
		'mozRequestFullScreen',
		'mozCancelFullScreen',
		'mozFullScreenElement',
		'mozFullScreenEnabled',
		'mozfullscreenchange',
		'mozfullscreenerror',
	],
	[
		'msRequestFullscreen',
		'msExitFullscreen',
		'msFullscreenElement',
		'msFullscreenEnabled',
		'MSFullscreenChange',
		'MSFullscreenError',
	],
];

interface NativeAPI {
	requestFullscreen: string;
	exitFullscreen: string;
	fullscreenElement: string;
	fullscreenEnabled: string;
	fullscreenchange: string;
	fullscreenerror: string;
}

const nativeAPI = (() => {
	if (typeof document === 'undefined') {
		return false;
	}

	const unprefixedMethods = methodMap[0]!;
	const returnValue: Record<string, string> = {};

	for (const methodList of methodMap) {
		const exitFullscreenMethod = methodList?.[1];
		if (exitFullscreenMethod && exitFullscreenMethod in document) {
			for (const [index, method] of methodList.entries()) {
				returnValue[unprefixedMethods[index]!] = method;
			}

			return returnValue as unknown as NativeAPI;
		}
	}

	return false;
})();

const eventNameMap: Record<string, string | undefined> = nativeAPI ? {
	change: nativeAPI.fullscreenchange,
	error: nativeAPI.fullscreenerror,
} : { change: undefined, error: undefined };

interface Screenfull {
	request: (element?: HTMLElement, options?: any) => Promise<void>;
	exit: () => Promise<void>;
	toggle: (element?: HTMLElement, options?: any) => Promise<void>;
	onchange: (callback: EventListener) => void;
	onerror: (callback: EventListener) => void;
	on: (event: string, callback: EventListener) => void;
	off: (event: string, callback: EventListener) => void;
	raw: typeof nativeAPI;
	isFullscreen?: boolean;
	element?: Element;
	isEnabled?: boolean;
}

let screenfull: Screenfull = {
	request(element = document.documentElement, options) {
		return new Promise<void>((resolve, reject) => {
			const onFullScreenEntered = () => {
				screenfull.off('change', onFullScreenEntered);
				resolve();
			};

			screenfull.on('change', onFullScreenEntered);

			if (!nativeAPI) {
				reject(new Error('Fullscreen API not supported'));
				return;
			}

			const returnPromise = (element as any)[nativeAPI.requestFullscreen](options);

			if (returnPromise instanceof Promise) {
				returnPromise.then(onFullScreenEntered).catch(reject);
			}
		});
	},
	exit() {
		return new Promise<void>((resolve, reject) => {
			if (!screenfull.isFullscreen) {
				resolve();
				return;
			}

			const onFullScreenExit = () => {
				screenfull.off('change', onFullScreenExit);
				resolve();
			};

			screenfull.on('change', onFullScreenExit);

			if (!nativeAPI) {
				reject(new Error('Fullscreen API not supported'));
				return;
			}

			const returnPromise = (document as any)[nativeAPI.exitFullscreen]();

			if (returnPromise instanceof Promise) {
				returnPromise.then(onFullScreenExit).catch(reject);
			}
		});
	},
	toggle(element?: HTMLElement, options?: any) {
		return screenfull.isFullscreen ? screenfull.exit() : screenfull.request(element, options);
	},
	onchange(callback: EventListener) {
		screenfull.on('change', callback);
	},
	onerror(callback: EventListener) {
		screenfull.on('error', callback);
	},
	on(event: string, callback: EventListener) {
		const eventName = eventNameMap[event];
		if (eventName) {
			document.addEventListener(eventName, callback, false);
		}
	},
	off(event: string, callback: EventListener) {
		const eventName = eventNameMap[event];
		if (eventName) {
			document.removeEventListener(eventName, callback, false);
		}
	},
	raw: nativeAPI,
};

if (nativeAPI) {
	Object.defineProperties(screenfull, {
		isFullscreen: {
			get: () => Boolean((document as any)[nativeAPI.fullscreenElement]),
		},
		element: {
			enumerable: true,
			get: () => (document as any)[nativeAPI.fullscreenElement] ?? undefined,
		},
		isEnabled: {
			enumerable: true,
			get: () => Boolean((document as any)[nativeAPI.fullscreenEnabled]),
		},
	});
} else {
	screenfull = { isEnabled: false } as Screenfull;
}

export { screenfull };
