package com.andrewkeeton.divide.and.conquer.card.shuffler;

import static junit.framework.Assert.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PileOfCards<T> {
	
	private static SecureRandom mRandomSeeder = new SecureRandom();
	private static SecureRandom mRandomShuffler = new SecureRandom();
	
	public ArrayList<Card<T>> mCards;
	public int mMinKey, mMaxKey;
	
	public PileOfCards() {
		mCards = new ArrayList<Card<T>>();
	}
	
	public PileOfCards(List<T> values, int minKey, int maxKey) {
		int numCards = maxKey - minKey + 1;
		if (values != null) {
			assertEquals("values.size() == numCards", numCards, values.size());
		}
		
		mCards = new ArrayList<Card<T>>(numCards);
		mMinKey = minKey;
		mMaxKey = maxKey;
		
		for (int i = 0; i < numCards; i++) {
			if (values != null) {
				mCards.add(new Card<T>(minKey + i, values.get(i)));
			} else {
				mCards.add(new Card<T>(minKey + i, null));
			}
		}
	}
	
	/**
	 * Sorts the pile of cards into separate piles (a la bucket sort) and returns the new piles.
	 * The list of moves is added to the moves list.
	 * @param numOutPiles The number of piles to sort into.
	 * @param moves Pile numbers are added to this list as the pile is sorted.
	 */
	public ArrayList<PileOfCards<T>> sortIntoPiles(int numOutPiles, ArrayList<Move> moves) {
		ArrayList<PileOfCards<T>> outPiles = new ArrayList<PileOfCards<T>>(numOutPiles);
		PileOfCards<T> inPile = this;
		
		int numCards = inPile.mMaxKey - inPile.mMinKey + 1;
		int baseNumCardsPerOutPile = numCards / numOutPiles;
		int remainingNumCards = numCards % numOutPiles;
		int minKey = inPile.mMinKey;
		
		// Distribute the key ranges amongst the out piles.
		for (int i = 0; i < numOutPiles; i++) {
			PileOfCards<T> outPile = new PileOfCards<T>();
			outPile.mMinKey = minKey;
			outPile.mMaxKey = minKey + baseNumCardsPerOutPile - 1;
			
			// Distribute one remaining card to each out pile while there are still remaining cards.
			if (remainingNumCards > 0) {
				outPile.mMaxKey++;
				remainingNumCards--;
			}
			
			minKey = outPile.mMaxKey + 1;
			
			outPiles.add(outPile);
		}
		
		/// Move the cards from the in pile into the appropriate out pile.
		for (Card<T> card : inPile.mCards) {
			int pileNum = 0;
			
			for (PileOfCards<T> outPile : outPiles) {
				if (card.mKey >= outPile.mMinKey && card.mKey <= outPile.mMaxKey) {
					outPile.mCards.add(0, card);
					moves.add(new Move(Move.MoveType.DEAL, pileNum));
					
					break;
				}
				
				pileNum++;
			}
		}
		///
		
		return outPiles;
	}
	
	/**
	 * Sorts this deck (pile) completely using a series of deals, pickups, and "bottoms."
	 * @param numOutPiles Number of piles to deal into.
	 * @return A list of moves that describe how this pile was sorted.
	 */
	public ArrayList<Move> sortDeckCompletely(int numOutPiles) {
		int deckStartingSize = this.size();
		
		// The starting deck (pile) will soon split into a deck consisting of many piles.
		ArrayList<PileOfCards<T>> deck = new ArrayList<PileOfCards<T>>();
		deck.add(this);
		
		final int maxNumMoves = (int) Math.ceil(logOfBase(numOutPiles, deckStartingSize)) * deckStartingSize;
		int numMovesLeft = maxNumMoves;
		ArrayList<Move> moves = new ArrayList<Move>(maxNumMoves);
		
		PileOfCards<T> deckPile = null;
		while (true) {
			// Check for piles of one.
			int numCardsMovedToBottom = 0;
			while (true) {
				if (numMovesLeft == 0) {
					break;
				}
				
				deckPile = deck.remove(0);
				if (deckPile.size() != 1) {
					break;
				}
				
				// Piles of one don't need to be sorted - just move them to the bottom.
				numMovesLeft--;
				numCardsMovedToBottom++;
				deck.add(deckPile);
			}
			
			if (numCardsMovedToBottom > 0) {
				moves.add(new Move(Move.MoveType.BOTTOM, numCardsMovedToBottom));
			}
			
			if (numMovesLeft == 0) {
				break;
			}
			
			assertTrue("deckPile.size() > 1", deckPile.size() > 1);
			
			// Sort piles from the deck that have more than one card into new piles.
			// Then pick up those piles and put them on the bottom of the deck.

			numMovesLeft -= deckPile.size();
			ArrayList<PileOfCards<T>> piles = deckPile.sortIntoPiles(numOutPiles, moves);

			int sizeOfPiles = 0;
			for (PileOfCards<T> pile : piles) {
				if (pile.size() > 0) {
					sizeOfPiles += pile.size();
					deck.add(pile);
				}
			}

			moves.add(new Move(Move.MoveType.PICKUP, sizeOfPiles));
			
			assertTrue("numMovesLeft >= 0", numMovesLeft >= 0);
			
			if (numMovesLeft == 0) {
				break;
			}
			
		}
		
		/// Make this pile look like the deck of one-card piles.
		mCards.clear();
		Card<T> cardPrev = null;
		for (PileOfCards<T> pile : deck) {
			assertTrue("pile.size() == 1", pile.size() == 1);
			
			Card<T> card = pile.mCards.get(0);
			if (cardPrev != null) {
				assertTrue("card.mKey > cardPrev.mKey", card.mKey > cardPrev.mKey);
				cardPrev = card;
			}
			mCards.add(pile.mCards.get(0));
		}
		///
		
		assertTrue("this.size() == deckStartingSize", this.size() == deckStartingSize);
		
		return moves;
	}
	
	public void setCardValues(List<T> values) {
		assertTrue("values.size() == mCards.size()", values.size() == mCards.size());
		
		for (int i = 0; i < values.size(); i++) {
			mCards.get(i).mValue = values.get(i);
		}
	}
	
	public void shuffle() {
		shuffle(mRandomShuffler, 8, 134);	// (2^[64 bits])^134 > 999!
	}
	
	public void shuffle(long seed) {
		Collections.shuffle(mCards, new Random(seed));
	}
	
	public void shuffle(Random random, int numSeedBytes, int numShuffles) {
		for (int i = 0; i < numShuffles; i++) {
			byte seedBytes[] = mRandomSeeder.generateSeed(numSeedBytes);
			long seed = 0;
			
			for (int j = 0; j < seedBytes.length; j++) {
				seed |= (((long) seedBytes[j]) & 0xFF) << (j * Byte.SIZE);
			}
			
			random.setSeed(seed);
			Collections.shuffle(mCards, random);
		}
	}
	
	public int size() {
		return mCards.size();
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		
		for (Card<T> card : mCards) {
			str.append(card.toString());
			str.append(", ");
		}
		
		str.setLength(str.length() - 2);
		
		return str.toString();
	}
	
	public String toStringSmall() {
		StringBuilder str = new StringBuilder();
		
		for (Card<T> card : mCards) {
			str.append(card.toString());
		}
		
		return str.toString();
	}
	
	public static double logOfBase(double base, double num) {
		return Math.log(num) / Math.log(base);
	}
	
	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for ( int j = 0; j < bytes.length; j++ ) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
