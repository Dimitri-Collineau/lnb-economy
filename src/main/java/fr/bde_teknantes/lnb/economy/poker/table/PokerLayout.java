/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class PokerLayout {
   public static final int SEATS = 3;
   public static final double[] SEAT_X = new double[]{17.0, 32.0, 47.0};
   public static final double SEAT_CARD_Z = 24.8;
   public static final double SEAT_CARD_DX = 2.9;
   public static final double[] BOARD_X = new double[]{18.0, 25.0, 32.0, 39.0, 46.0};
   public static final double BOARD_Z = 16.8;
   public static final double BET_Z = 31.4;
   public static final double ANTE_DX = -3.6;
   public static final double CALL_DX = 3.4;
   public static final double DEPTH = 40.0;

   private PokerLayout() {
   }

   public static BlackjackLayout.Slot dealerCard(int index) {
      if (index < 2) {
         return new BlackjackLayout.Slot(BlackjackLayout.DEALER_SLOT_X[index], 13.8, 9.4, -Math.PI / 2);
      } else {
         int b = Math.min(index - 2, BOARD_X.length - 1);
         return new BlackjackLayout.Slot(BOARD_X[b], 13.8, 16.8, -Math.PI / 2);
      }
   }

   public static BlackjackLayout.Slot playerCard(int seat, int index) {
      double x = SEAT_X[clampSeat(seat)] + (index == 0 ? -2.9 : 2.9);
      return new BlackjackLayout.Slot(x, 13.8, 24.8, -Math.PI / 2);
   }

   public static BlackjackLayout.Slot slot(boolean toDealer, int seat, int index) {
      return toDealer ? dealerCard(index) : playerCard(seat, index);
   }

   public static double[] seatCenter(int seat) {
      return new double[]{SEAT_X[clampSeat(seat)], 31.4};
   }

   public static int nearestSeat(double x, double z) {
      int best = 1;
      double bestDistance = Double.MAX_VALUE;

      for (int i = 0; i < 3; i++) {
         double distance = Math.abs(x - SEAT_X[i]);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      return best;
   }

   static int clampSeat(int seat) {
      return Math.max(0, Math.min(2, seat));
   }
}
