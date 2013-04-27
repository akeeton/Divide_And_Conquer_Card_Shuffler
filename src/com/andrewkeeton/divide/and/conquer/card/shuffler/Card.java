package com.andrewkeeton.divide.and.conquer.card.shuffler;

public class Card<T> implements Comparable<Card<T>> {
	public Integer mKey;
	public T mValue;
	
	public Card() {}
	
	public Card(int key, T value) {
		mKey = key;
		mValue = value;
	}
	
	public int compareTo(Card<T> another) {
		return mKey.compareTo(another.mKey);
	}
	
	@Override
	public String toString() {
		if (mValue != null) {
			return mValue.toString();
		} else {
			return "-";
		}
	}
}