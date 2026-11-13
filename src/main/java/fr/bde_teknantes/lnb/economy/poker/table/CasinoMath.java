/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class CasinoMath {
   private CasinoMath() {
   }

   public static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
