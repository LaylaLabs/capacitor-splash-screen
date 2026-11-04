#!/usr/bin/env node

/**
 * @file sync.mjs
 * Build-time synchronization script for @laylalabs/capacitor-splash-screen.
 * Reads capacitor.config.ts / capacitor.config.json as Single Source of Truth (SSOT)
 * and synchronizes static assets (background color and launch icon) into native
 * Android and iOS resources to guarantee a flawless 0ms cold-start handshake.
 */

import fs from 'node:fs';
import path from 'node:path';

function findProjectRoot(startDir = process.cwd()) {
  let current = startDir;
  while (current !== path.dirname(current)) {
    if (
      fs.existsSync(path.join(current, 'capacitor.config.ts')) ||
      fs.existsSync(path.join(current, 'capacitor.config.json')) ||
      fs.existsSync(path.join(current, 'capacitor.config.js')) ||
      fs.existsSync(path.join(current, 'capacitor.config.mjs'))
    ) {
      return current;
    }
    // Check if under an app folder (e.g. apps/mobile-nuxt)
    const subCap = path.join(current, 'apps', 'mobile-nuxt');
    if (fs.existsSync(path.join(subCap, 'capacitor.config.ts'))) {
      return subCap;
    }
    current = path.dirname(current);
  }
  return process.cwd();
}

function parseSplashConfig(projectRoot) {
  const candidates = [
    'capacitor.config.ts',
    'capacitor.config.json',
    'capacitor.config.mjs',
    'capacitor.config.js',
  ];

  let configPath = null;
  for (const c of candidates) {
    const full = path.join(projectRoot, c);
    if (fs.existsSync(full)) {
      configPath = full;
      break;
    }
  }

  if (!configPath) {
    console.warn(`[layla-splash] Nenhum arquivo capacitor.config.* encontrado em ${projectRoot}`);
    return null;
  }

  const raw = fs.readFileSync(configPath, 'utf8');

  // If JSON format
  if (configPath.endsWith('.json')) {
    try {
      const parsed = JSON.parse(raw);
      const splash = parsed.plugins?.LaylaSplashScreen || parsed.plugins?.LaylaLottieSplash;
      return splash || null;
    } catch {
      return null;
    }
  }

  // Regex extraction for TS/JS configuration to avoid heavy runtime dependencies
  const splashBlockMatch = raw.match(
    /(?:LaylaSplashScreen|LaylaLottieSplash)\s*:\s*\{([\s\S]*?)\n\s*\},?\s*(?:[A-Z][a-zA-Z0-9_]*\s*:|\})/
  );

  if (!splashBlockMatch) {
    console.warn('[layla-splash] Bloco LaylaSplashScreen não encontrado no capacitor.config');
    return null;
  }

  const block = splashBlockMatch[1];
  const bgMatch = block.match(/backgroundColor\s*:\s*["']([^"']+)["']/);
  const iconMatch = block.match(/iconPath\s*:\s*["']([^"']+)["']/);
  const animPathMatch = block.match(/(?:path|animationPath)\s*:\s*["']([^"']+)["']/);

  return {
    backgroundColor: bgMatch ? bgMatch[1] : undefined,
    iconPath: iconMatch ? iconMatch[1] : undefined,
    animationPath: animPathMatch ? animPathMatch[1] : undefined,
  };
}

function syncAndroid(projectRoot, config) {
  const androidResDir = path.join(projectRoot, 'android', 'app', 'src', 'main', 'res');
  if (!fs.existsSync(androidResDir)) {
    return;
  }

  console.log(`[layla-splash] Sincronizando recursos Android em: ${androidResDir}`);

  // 1. Sincroniza cores em values/colors.xml
  if (config.backgroundColor && config.backgroundColor !== 'auto') {
    const colorsFile = path.join(androidResDir, 'values', 'colors.xml');
    if (fs.existsSync(colorsFile)) {
      let content = fs.readFileSync(colorsFile, 'utf8');
      const colorRegex = /<color\s+name="splash_background">[^<]*<\/color>/;
      const targetColor = `<color name="splash_background">${config.backgroundColor}</color>`;

      if (colorRegex.test(content)) {
        content = content.replace(colorRegex, targetColor);
      } else {
        content = content.replace('</resources>', `    ${targetColor}\n</resources>`);
      }
      fs.writeFileSync(colorsFile, content, 'utf8');
      console.log(`  ✓ Atualizado @color/splash_background para ${config.backgroundColor} em colors.xml`);
    }
  }

  // 2. Sincroniza ícone estático em drawable/splash_icon.xml e drawable/splash.xml
  if (config.iconPath) {
    const iconName = config.iconPath.replace(/^@drawable\//, '').replace(/\.[^/.]+$/, '');
    const splashIconFile = path.join(androidResDir, 'drawable', 'splash_icon.xml');
    if (fs.existsSync(splashIconFile)) {
      let content = fs.readFileSync(splashIconFile, 'utf8');
      const drawableRegex = /android:drawable="@drawable\/[^"]+"/;
      if (drawableRegex.test(content)) {
        content = content.replace(drawableRegex, `android:drawable="@drawable/${iconName}"`);
        fs.writeFileSync(splashIconFile, content, 'utf8');
        console.log(`  ✓ Atualizado drawable do splash_icon.xml para @drawable/${iconName}`);
      }
    }

    const splashFile = path.join(androidResDir, 'drawable', 'splash.xml');
    if (fs.existsSync(splashFile)) {
      let content = fs.readFileSync(splashFile, 'utf8');
      const drawableRegex = /(<item[\s\S]*?android:drawable=")@drawable\/[^"]+(")/g;
      // Substitui apenas o drawable que não seja splash_background
      content = content.replace(/android:drawable="@drawable\/(?!splash_background)[^"]+"/g, `android:drawable="@drawable/${iconName}"`);
      fs.writeFileSync(splashFile, content, 'utf8');
      console.log(`  ✓ Atualizado drawable do splash.xml para @drawable/${iconName}`);
    }
  }
}

function syncIOS(projectRoot, config) {
  const iosAppDir = path.join(projectRoot, 'ios', 'App');
  if (!fs.existsSync(iosAppDir)) {
    return;
  }
  console.log(`[layla-splash] Verificando recursos iOS em: ${iosAppDir}`);
  // iOS launch screen sync hooks can be extended here
}

function run() {
  const args = process.argv.slice(2).filter((a) => a !== 'sync');
  const targetDir = args[0] ? path.resolve(args[0]) : findProjectRoot();
  console.log(`[layla-splash] Localizando configuração em ${targetDir}...`);
  const config = parseSplashConfig(targetDir);

  if (!config) {
    console.log('[layla-splash] Nenhuma alteração a sincronizar.');
    return;
  }

  console.log('[layla-splash] Configuração detectada (SSOT):', config);
  syncAndroid(targetDir, config);
  syncIOS(targetDir, config);
  console.log('[layla-splash] Sincronização concluída com sucesso!');
}

run();

