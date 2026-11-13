/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import java.util.List;

public final class BlackjackCards {
   public static final int HIDDEN = -1;
   public static final int DECK = 52;

   private BlackjackCards() {
   }

   public static int rank(int card) {
      return card % 13;
   }

   public static int suit(int card) {
      return card / 13;
   }

   public static boolean isRed(int card) {
      return suit(card) == 1 || suit(card) == 2;
   }

   public static int pips(int card) {
      return Math.min(rank(card) + 1, 10);
   }

   public static boolean isTenValue(int card) {
      return rank(card) >= 9;
   }

   public static boolean isAce(int card) {
      return rank(card) == 0;
   }

   public static String rankName(int card) {
      int r = rank(card);
      return r == 0 ? "A" : (r < 10 ? Integer.toString(r + 1) : "JQK".substring(r - 10, r - 9));
   }

   public static int total(List<Integer> cards) {
      int sum = 0;
      int aces = 0;

      for (int card : cards) {
         if (card != -1) {
            sum += pips(card);
            if (isAce(card)) {
               aces++;
            }
         }
      }

      if (aces > 0 && sum + 10 <= 21) {
         sum += 10;
      }

      return sum;
   }

   public static boolean isSoft(List<Integer> cards) {
      int sum = 0;
      boolean ace = false;

      for (int card : cards) {
         if (card != -1) {
            sum += pips(card);
            ace |= isAce(card);
         }
      }

      return ace && sum + 10 <= 21;
   }

   public static boolean isBlackjack(List<Integer> cards) {
      return cards.size() == 2 && cards.get(0) != -1 && cards.get(1) != -1 && total(cards) == 21;
   }
}
