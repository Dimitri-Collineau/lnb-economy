/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class BlackjackLayout {
   public static final int SPOTS = 5;
   public static final double CENTER_X = 32.0;
   public static final double[] SPOT_ANGLE = new double[]{30.0, 60.0, 90.0, 120.0, 150.0};
   public static final double SPOT_RADIUS = 23.6;
   public static final double CARD_RADIUS = 16.7;
   public static final double CARD_W = 5.0;
   public static final double CARD_L = 6.4;
   public static final double FELT_Y = 13.5;
   public static final double CARD_Y = 13.8;
   public static final double LAYER = 0.06;
   public static final double FAN = 3.0;
   public static final double[] DEALER_SLOT_X = new double[]{28.2, 35.8};
   public static final double DEALER_CARD_Z = 9.4;
   public static final double DEALER_FAN_START = 41.2;
   public static final double DEALER_FAN = 3.4;
   public static final double SHOE_X = 50.5;
   public static final double SHOE_Z = 3.0;
   public static final double SHOE_Y = 15.3;
   public static final double TRAY_X = 32.0;
   public static final double TRAY_Z = 3.4;

   private BlackjackLayout() {
   }

   public static double[] spotCenter(int spot) {
      double a = Math.toRadians(SPOT_ANGLE[spot]);
      return new double[]{32.0 + 23.6 * Math.cos(a), 23.6 * Math.sin(a)};
   }

   public static BlackjackLayout.Slot playerCard(int spot, int index) {
      double a = Math.toRadians(SPOT_ANGLE[spot]);
      double bx = 32.0 + 16.7 * Math.cos(a);
      double bz = 16.7 * Math.sin(a);
      double side = Math.cos(a) > 1.0E-9 ? -1.0 : 1.0;
      double ux = side * Math.sin(a);
      double uz = side * -Math.cos(a);
      double off = (index - 0.5) * 3.0;
      return new BlackjackLayout.Slot(bx + ux * off, 13.8 + 0.06 * index, bz + uz * off, a + Math.PI);
   }

   public static BlackjackLayout.Slot dealerCard(int index) {
      double x = index < 2 ? DEALER_SLOT_X[index] : 41.2 + 3.4 * (index - 2);
      return new BlackjackLayout.Slot(x, 13.8 + 0.06 * index, 9.4, -Math.PI / 2);
   }

   public static BlackjackLayout.Slot slot(boolean toDealer, int spot, int index) {
      return toDealer ? dealerCard(index) : playerCard(spot, index);
   }

   public static double bearing(double x, double z) {
      return Math.atan2(x - 32.0, z - -4.6);
   }

   public static int nearestSpot(double x, double z) {
      double angle = Math.toDegrees(Math.atan2(z, x - 32.0));
      int best = 2;
      double bestDelta = Double.MAX_VALUE;

      for (int i = 0; i < 5; i++) {
         double delta = Math.abs(angle - SPOT_ANGLE[i]);
         if (delta < bestDelta) {
            bestDelta = delta;
            best = i;
         }
      }

      return best;
   }

   public static double[] toBlock(double x, double y, double z, int turns) {
      double dx = x - 24.0;
      double dz = z - 24.0;

      for (int i = 0; i < turns; i++) {
         double nx = -dz;
         dz = dx;
         dx = nx;
      }

      return new double[]{0.5 + dx / 16.0, y / 16.0, 0.5 + dz / 16.0};
   }

   public record Slot(double x, double y, double z, double yaw) {
   }
}
