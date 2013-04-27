package com.andrewkeeton.divide.and.conquer.card.shuffler;

import static junit.framework.Assert.*;

import java.util.ArrayList;

import android.os.Parcel;
import android.os.Parcelable;

// TODO: Make serializable.
public class Move implements Parcelable {
	
	public enum MoveType {
		DEAL, PICKUP, BOTTOM, NONE
	};

	public MoveType mType;
	public int mValue;
	
	public Move() {
		this(MoveType.NONE, -1);
	}
	
	public Move(MoveType type, int value) {
		mType = type;
		mValue = value;
	}
	
	@Override
	public boolean equals(Object obj) {
		Move move1 = this;
		Move move2 = (Move) obj;
		
		return (move1.mType == move2.mType) && (move1.mValue == move2.mValue);
	}
	
	@Override
	public String toString() {
		String prefix;
		
		switch(mType) {
		case DEAL:
			prefix = "D";
			break;
		case PICKUP:
			prefix = "P";
			break;
		case BOTTOM:
			prefix = "B";
			break;
		case NONE:
			prefix = "N";
			break;
		default:
			prefix = "E";
			fail();
			break;
		}
		
		return prefix + mValue;
	}
	
	static public Move fromString(String str) {
		assertTrue("str.length() >= 2", str.length() >= 2);
		
		MoveType type = MoveType.NONE;
		int value = -1;
		
		switch (str.charAt(0)) {
		case 'D':
			type = MoveType.DEAL;
			break;
		case 'P':
			type = MoveType.PICKUP;
			break;
		case 'B':
			type = MoveType.BOTTOM;
			break;
		case 'N':
			type = MoveType.NONE;
			break;
		case 'E':
			fail();
			break;
		default:
			fail();
			break;
		};
		
		value = Integer.valueOf(str.substring(1));
		
		Move move = new Move(type, value);
		//Log.w(MainActivity.TAG_MOVE, str + " -> " + move.toString());
		
		return move;
	}
	
	static public ArrayList<Move> arrayListFromString(String str) {
		ArrayList<Move> moves = new ArrayList<Move>();
		str = str.substring(1, str.length() - 1);
		if (str.length() == 0) {
			return moves;
		}
		
		String strMoves[] = str.split(", ");
		
		for (String strMove : strMoves) {
			moves.add(Move.fromString(strMove));
		}
		
		//Log.w(MainActivity.TAG_MOVE, str + " -> " + moves.toString());
		
		return moves;
	}
	
	/// Parcelable methods
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(mType.ordinal());
		out.writeInt(mValue);
	}
	
	public static final Parcelable.Creator<Move> CREATOR = new Parcelable.Creator<Move>() {
		
		public Move createFromParcel(Parcel in) {
			return new Move(in);
		}
		
		public Move[] newArray(int size) {
			return new Move[size];
		}
	};
	
	private Move(Parcel in) {
		mType = MoveType.values()[in.readInt()];
		mValue = in.readInt();
	}
	///
}