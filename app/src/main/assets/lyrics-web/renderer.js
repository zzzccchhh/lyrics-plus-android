(function () {
  var lyricsEl = document.getElementById("lyrics");
  var emptyEl = document.getElementById("empty");
  var stageEl = document.getElementById("stage");
  var lyricsViewportEl = null;

  function getScrollContainer() {
    if (!lyricsViewportEl) {
      lyricsViewportEl = document.querySelector(".lyrics-viewport") || stageEl;
    }
    return state.isFullLyricsMode ? lyricsViewportEl : stageEl;
  }
  var trackTitleEl = document.getElementById("trackTitle");
  var trackArtistEl = document.getElementById("trackArtist");

  var state = {
    track: null,
    lyrics: [],
    activeIndex: -1,
    showRomaji: true,
    animationRunning: false,
    isFullLyricsMode: false
  };

  var playback = {
    positionMs: 0,
    isPlaying: false,
    updatedAt: 0
  };

  function report(message) {
    try {
      if (window.console && window.console.log) {
        window.console.log("LyricsPlus " + message);
      }
      if (window.AndroidLyrics && window.AndroidLyrics.report) {
        window.AndroidLyrics.report(String(message));
      }
    } catch (ignore) {
    }
  }

  function hashColor(seed, sat, light) {
    var hash = 0;
    for (var i = 0; i < seed.length; i += 1) {
      hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
    }
    return "hsl(" + (hash % 360) + " " + sat + "% " + light + "%)";
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function setTrack(track) {
    try {
      var seed = String((track && track.track) || "") + String((track && track.artist) || "");
      var startColor = (track && track.backgroundStart) || hashColor(seed || "lyrics", 68, 48);
      var endColor = (track && track.backgroundEnd) || hashColor(seed + " deep", 42, 24);
      var accentColor = hashColor(seed + " accent", 74, 62);
      state.track = track || null;
      trackTitleEl.textContent = (track && track.track) || "Lyrics Plus";
      trackArtistEl.textContent = (track && track.artist) || "Waiting for Spotify";
      stageEl.style.setProperty("--bg-a", startColor);
      stageEl.style.setProperty("--bg-b", endColor);
      stageEl.style.setProperty("--bg-c", accentColor);
      stageEl.setAttribute("data-track-key", seed);
      report("track: " + seed + ", colors: " + startColor + " -> " + endColor);
    } catch (error) {
      report("error:setTrack:" + error.message);
    }
  }

  function setLyrics(lines) {
    try {
      state.lyrics = Object.prototype.toString.call(lines) === "[object Array]" ? lines : [];
      if (state.lyrics.length > 0) {
        var firstLineStart = Number(state.lyrics[0].startTimeMs || 0);
        if (firstLineStart > 2000) {
          state.lyrics.unshift({
            startTimeMs: 0,
            text: "•••",
            translation: "",
            reading: ""
          });
        }
      }
      emptyEl.hidden = state.lyrics.length > 0;
      if (state.lyrics.length > 0) {
        if (state.activeIndex < 0 || state.activeIndex >= state.lyrics.length) {
          state.activeIndex = 0;
        }
      } else {
        state.activeIndex = -1;
      }
      // Reset scroll position when new lyrics arrive (e.g. track change)
      if (state.isFullLyricsMode) {
        getScrollContainer().scrollTop = 0;
      }
      // Full rebuild: new lyrics data means new DOM
      renderFull();
      report("lyrics: " + state.lyrics.length + ", active: " + state.activeIndex);
    } catch (error) {
      report("error:setLyrics:" + error.message);
    }
  }

  function setPlaybackState(positionMs, isPlaying) {
    try {
      playback.positionMs = Number(positionMs) || 0;
      playback.isPlaying = !!isPlaying;
      playback.updatedAt = performance.now();
      updatePlaybackPosition();
    } catch (error) {
      report("error:setPlaybackState:" + error.message);
    }
  }

  function setAnimationDuration(seconds) {
    try {
      stageEl.style.setProperty("--flow-duration", Number(seconds) + "s");
      report("setAnimationDuration: " + seconds + "s");
    } catch (error) {
      report("error:setAnimationDuration:" + error.message);
    }
  }

  function setShowRomaji(show) {
    try {
      state.showRomaji = !!show;
      // Romaji toggle requires DOM content changes, so do a full render
      renderFull();
      report("setShowRomaji: " + state.showRomaji);
    } catch (error) {
      report("error:setShowRomaji:" + error.message);
    }
  }

  function setAnimationPlayState(running) {
    try {
      state.animationRunning = !!running;
      if (state.animationRunning) {
        stageEl.classList.remove("animation-paused");
        stageEl.classList.add("animation-running");
      } else {
        stageEl.classList.remove("animation-running");
        stageEl.classList.add("animation-paused");
      }
      report("setAnimationPlayState: " + (running ? "running" : "paused"));
    } catch (error) {
      report("error:setAnimationPlayState:" + error.message);
    }
  }

  function updatePlaybackPosition() {
    if (!state.lyrics.length) return;
    var elapsed = playback.isPlaying ? (performance.now() - playback.updatedAt) : 0;
    var currentPosition = playback.positionMs + elapsed;
    var nextIndex = findActiveIndex(currentPosition);
    if (nextIndex !== state.activeIndex) {
      state.activeIndex = nextIndex;
      if (state.isFullLyricsMode) {
        // Lightweight update: just swap CSS classes, no DOM re-render
        updateActiveClassesOnly();
      } else {
        // Incremental update: patch styles and classes, no DOM rebuild
        patchFocusedMode();
      }
      report("playback: " + Math.round(currentPosition / 1000) + "s, active: " + state.activeIndex);
    }
  }

  function tick() {
    if (playback.isPlaying) {
      updatePlaybackPosition();
    }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);

  function findActiveIndex(positionMs) {
    var result = 0;
    for (var i = 0; i < state.lyrics.length; i += 1) {
      if (positionMs >= Number(state.lyrics[i].startTimeMs || 0)) {
        result = i;
      } else {
        break;
      }
    }
    return result;
  }

  function clampedActiveIndex() {
    if (!state.lyrics.length) return -1;
    if (state.activeIndex < 0) return 0;
    if (state.activeIndex >= state.lyrics.length) return state.lyrics.length - 1;
    return state.activeIndex;
  }

  // ---------- Focused-mode helpers ----------

  function getDistanceStyle(distance) {
    // Past lines: fully hidden (no blur bar needed)
    if (distance < 0) {
      return "opacity:0;filter:blur(4px);transform:scale(0.78)";
    }
    var scale = distance === 0 ? 1 : Math.abs(distance) === 1 ? 0.88 : 0.78;
    var opacity = distance === 0 ? 1 : Math.abs(distance) === 1 ? 0.55 : 0.25;
    var blur = distance === 0 ? 0 : Math.abs(distance) === 1 ? 1.5 : 4;
    return "opacity:" + opacity + ";filter:blur(" + blur + "px);transform:scale(" + scale + ")";
  }

  function getKindClass(distance) {
    if (distance === 0) return "active";
    if (Math.abs(distance) === 1) return "near";
    return "far";
  }

  function getDirectionClass(distance) {
    if (distance < 0) return "past";
    if (distance > 0) return "future";
    return "";
  }

  function getLengthClass(line) {
    var textLen = (line.text || "").length;
    var transLen = (line.translation || "").length;
    var totalLen = textLen + transLen * 0.7;
    return totalLen > 65 ? "long" : totalLen < 12 ? "short" : "";
  }

  function buildLineInnerHTML(line, isActive) {
    var lineTextHtml = escapeHtml(line.text || "♪");
    if (line.text === "•••") {
      if (isActive) {
        lineTextHtml = '<span class="intro-dots"><span class="dot dot-1">•</span><span class="dot dot-2">•</span><span class="dot dot-3">•</span></span>';
      } else {
        lineTextHtml = '•••';
      }
    }

    var reading = "";
    if (line.reading && state.showRomaji) {
      // In focused mode, only show romaji on active line
      if (state.isFullLyricsMode || isActive) {
        reading = '<span class="romaji">' + escapeHtml(line.reading) + "</span>";
      }
    }

    var translation = line.translation ? '<span class="translation">' + escapeHtml(line.translation) + "</span>" : "";

    return reading + '<span class="text">' + lineTextHtml + "</span>" + translation;
  }

  /**
   * Incremental patch for focused (default) mode.
   * Only updates CSS classes, inline styles, and romaji/dot content where needed.
   * Does NOT destroy/recreate DOM nodes.
   */
  function patchFocusedMode() {
    var active = clampedActiveIndex();
    state.activeIndex = active;

    var lines = lyricsEl.querySelectorAll(".line");
    if (!lines.length) return;

    for (var i = 0; i < lines.length; i += 1) {
      var el = lines[i];
      var distance = i - active;
      var kind = getKindClass(distance);
      var direction = getDirectionClass(distance);

      // Update classes
      el.classList.remove("active", "near", "far", "past", "future");
      el.classList.add(kind);
      if (direction) el.classList.add(direction);

      // Update inline styles (these will transition via CSS)
      el.setAttribute("style", getDistanceStyle(distance));

      // Update dots innerHTML only for the "•••" intro line
      var lineData = state.lyrics[i];
      if (lineData && lineData.text === "•••") {
        var textSpan = el.querySelector(".text");
        if (textSpan) {
          if (distance === 0) {
            textSpan.innerHTML = '<span class="intro-dots"><span class="dot dot-1">•</span><span class="dot dot-2">•</span><span class="dot dot-3">•</span></span>';
          } else {
            textSpan.textContent = '•••';
          }
        }
      }

      // Update romaji visibility: show only on active line in focused mode
      if (lineData && lineData.reading && state.showRomaji) {
        var existingRomaji = el.querySelector(".romaji");
        if (distance === 0) {
          if (!existingRomaji) {
            var romajiSpan = document.createElement("span");
            romajiSpan.className = "romaji";
            romajiSpan.textContent = lineData.reading;
            el.insertBefore(romajiSpan, el.firstChild);
          }
        } else {
          if (existingRomaji) {
            existingRomaji.remove();
          }
        }
      }
    }

    // Smoothly move the container so the active line stays at the anchor position
    requestAnimationFrame(function () {
      if (lines[active]) {
        var activeLine = lines[active];
        var offset = activeLine.offsetTop;
        lyricsEl.style.transform = "translateY(-" + offset + "px)";
      }
    });
  }

  /**
   * Full render – builds or rebuilds all DOM nodes.
   * Called on initial load, lyrics change, romaji toggle, or mode switch.
   */
  function renderFull() {
    if (!state.lyrics.length) {
      lyricsEl.innerHTML = "";
      emptyEl.hidden = false;
      return;
    }

    var active = clampedActiveIndex();
    state.activeIndex = active;

    var html = "";
    for (var i = 0; i < state.lyrics.length; i += 1) {
      var line = state.lyrics[i] || {};
      var distance = i - active;
      var lengthClass = getLengthClass(line);
      var kind = getKindClass(distance);
      var direction = getDirectionClass(distance);

      var isActive = distance === 0;

      if (state.isFullLyricsMode) {
        html += '<article class="line ' + kind + " " + direction + " " + lengthClass + '"' +
          ' data-index="' + i + '">' +
          buildLineInnerHTML(line, isActive) +
          "</article>";
      } else {
        html += '<article class="line ' + kind + " " + direction + " " + lengthClass + '"' +
          ' data-index="' + i + '"' +
          ' style="' + getDistanceStyle(distance) + '">' +
          buildLineInnerHTML(line, isActive) +
          "</article>";
      }
    }

    lyricsEl.innerHTML = html;

    requestAnimationFrame(function () {
      var lines = lyricsEl.querySelectorAll(".line");
      if (lines[active]) {
        var activeLine = lines[active];
        var offset = activeLine.offsetTop;
        if (!state.isFullLyricsMode) {
          lyricsEl.style.transform = "translateY(-" + offset + "px)";
        } else {
          lyricsEl.style.transform = "none";
          scrollToActiveIfNeeded(activeLine);
        }
      }
    });

    report("render: active=" + active + " total=" + state.lyrics.length);
  }

  // Lightweight active-line update for full-lyrics-mode: swap classes without re-rendering DOM
  function updateActiveClassesOnly() {
    var active = clampedActiveIndex();
    var lines = lyricsEl.querySelectorAll(".line");
    if (!lines.length) return;

    for (var i = 0; i < lines.length; i += 1) {
      var el = lines[i];
      var distance = i - active;
      // Remove old state classes
      el.classList.remove("active", "near", "far", "past", "future");
      // Add new state classes
      if (distance === 0) {
        el.classList.add("active");
      } else if (Math.abs(distance) === 1) {
        el.classList.add("near");
      } else {
        el.classList.add("far");
      }
      if (distance < 0) {
        el.classList.add("past");
      } else if (distance > 0) {
        el.classList.add("future");
      }
    }

    // Auto-scroll only if active line is out of viewport
    if (lines[active]) {
      scrollToActiveIfNeeded(lines[active]);
    }
  }




  window.LyricsPlus = {
    setTrack: setTrack,
    setLyrics: setLyrics,
    setPlaybackState: setPlaybackState,
    setAnimationDuration: setAnimationDuration,
    setShowRomaji: setShowRomaji,
    setAnimationPlayState: setAnimationPlayState
  };

  window.onerror = function (message, source, line) {
    report("error:" + message + "@" + line);
  };

  // ---------- Full-lyrics-mode: scroll-pause on user interaction ----------
  // When the user manually scrolls in full-lyrics-mode, we suppress auto-scroll
  // to the active line. After a period of no interaction, we resume auto-scroll.

  var SCROLL_RESUME_DELAY_MS = 3000; // 3 seconds after last touch → resume auto-scroll
  var userScrolling = false;
  var scrollResumeTimer = null;

  function onUserScrollInteraction() {
    if (!state.isFullLyricsMode) return;
    userScrolling = true;
    // Reset the resume timer
    if (scrollResumeTimer !== null) {
      clearTimeout(scrollResumeTimer);
    }
    scrollResumeTimer = setTimeout(function () {
      userScrolling = false;
      scrollResumeTimer = null;
      // Resume: immediately scroll to the current active line
      var lines = lyricsEl.querySelectorAll(".line");
      var active = clampedActiveIndex();
      if (lines[active]) {
        scrollToActiveIfNeeded(lines[active]);
      }
    }, SCROLL_RESUME_DELAY_MS);
  }

  // Listen for user-initiated scroll/touch on both possible scroll containers
  var viewportEl = document.querySelector(".lyrics-viewport");
  stageEl.addEventListener("touchstart", onUserScrollInteraction, { passive: true });
  stageEl.addEventListener("touchmove", onUserScrollInteraction, { passive: true });
  viewportEl.addEventListener("touchstart", onUserScrollInteraction, { passive: true });
  viewportEl.addEventListener("touchmove", onUserScrollInteraction, { passive: true });

  // Scroll active line — but only if the user isn't manually browsing
  function scrollToActiveIfNeeded(activeLine) {
    if (!state.isFullLyricsMode) return;
    if (userScrolling) return; // User is browsing, don't fight their scroll
    getScrollContainer().scrollTo({
      top: activeLine.offsetTop - getScrollContainer().offsetHeight * 0.05,
      behavior: "smooth"
    });
  }

  window.LyricsPlus = {
    setTrack: setTrack,
    setLyrics: setLyrics,
    setPlaybackState: setPlaybackState,
    setAnimationDuration: setAnimationDuration,
    setShowRomaji: setShowRomaji,
    setAnimationPlayState: setAnimationPlayState
  };

  window.onerror = function (message, source, line) {
    report("error:" + message + "@" + line);
  };

  // Click handler to toggle mode
  stageEl.addEventListener("click", function (e) {
    if (state.lyrics.length > 0) {
      toggleFullLyricsMode();
    }
  });

  function toggleFullLyricsMode() {
    state.isFullLyricsMode = !state.isFullLyricsMode;
    if (state.isFullLyricsMode) {
      userScrolling = false; // Reset scroll-pause state on enter
      stageEl.classList.add("full-lyrics-mode");
      // Reset scroll before rendering to avoid starting from previous position
      getScrollContainer().scrollTop = 0;
      renderFull();
      // Instantly jump to active line position (no smooth scroll from top)
      requestAnimationFrame(function () {
        var lines = lyricsEl.querySelectorAll(".line");
        if (lines[state.activeIndex]) {
          getScrollContainer().scrollTop = lines[state.activeIndex].offsetTop - window.innerHeight * 0.05;
        }
      });
    } else {
      userScrolling = false;
      if (scrollResumeTimer !== null) {
        clearTimeout(scrollResumeTimer);
        scrollResumeTimer = null;
      }
      stageEl.classList.remove("full-lyrics-mode");
      getScrollContainer().scrollTop = 0;
      // Disable transition to prevent upward bounce animation
      lyricsEl.style.transition = "none";
      renderFull();
      // Re-enable transition after position is painted
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          lyricsEl.style.transition = "";
        });
      });
    }
    report("toggleFullLyricsMode: " + state.isFullLyricsMode);
    try {
      if (window.AndroidLyrics && window.AndroidLyrics.setFullLyricsMode) {
        window.AndroidLyrics.setFullLyricsMode(state.isFullLyricsMode);
      }
    } catch (ignore) { }
  }

  report("renderer ready");
})();
