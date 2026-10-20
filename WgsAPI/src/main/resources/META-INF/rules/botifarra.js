/*
    Botifarra game
    Copyright (C) 2026 by Jordi Mariné Fort

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

var app = app || {}
app.model = app.model || {}
app.model.Botifarra = (function() {

var FREE_VARIANT = 0;         // Occidental (Lleida & Tarragona)
var OBLIGATORY_VARIANT = 1;   // Oriental (Barcelona & Girona)
var INTERMEDIATE_VARIANT = 2; // similar to Oriental variant, but not so restrictive

function Botifarra(points, variant) {
  if(!points) points = 101;
  if(!variant) variant = FREE_VARIANT;
  this.points = points;
  this.variant = variant;
  return this;
}



Botifarra.prototype = new app.model.Game();
Botifarra.prototype.constructor = Botifarra;
Botifarra.prototype.constructor.name = "Botifarra";

Botifarra.prototype.initFromStateStr = function(str) {
   var parts = [];
   if(str) parts = str.split('#');

   this.points = parts[1];
   this.variant = parts[2];
   this.winner = parseInt(parts[3]);
   this.turn = parseInt(parts[4]) + 1;
   this.step = parts[5];
   this.trump = parts[6];
   this.multiplier = parts[7];
   this.ask_pair_to_double = parts[8];
   this.score_team1 = parts[9];
   this.score_team2 = parts[10];
   this.currentRound = parts[11];
   this.lastRound = parts[12];
   this.owner = parts[13];
}

Botifarra.prototype.toString = function() {
   var retval = [];
   retval.push("Botifarra");
   retval.push(this.points);
   retval.push(this.variant);
   retval.push(this.winner);
   retval.push(this.getTurn() - 1);
   retval.push(this.step);
   retval.push(this.trump);
   retval.push(this.multiplier);
   retval.push(this.ask_pair_to_double);
   retval.push(this.score_team1);
   retval.push(this.score_team2);
   retval.push(this.currentRound);
   retval.push(this.lastRound);
   retval.push(this.owner);
   return retval.join("#");
}


Botifarra.prototype.isOver = function() {
   return ((this.winner != null && this.winner >= NONE) || Math.max(this.score_team1, this.score_team2) >= this.points);
}


Botifarra.prototype.getWinner = function() {
  if(this.winner != null && this.winner >= NONE) { 
    return this.winner;
  } else if(Math.max(this.score_team1, this.score_team2) < this.points) {
    return NONE;
  } else {
    if(this.score_team1 > this.score_team2) return 1;
    else if(this.score_team2 > this.score_team1) return 2;
    else return NONE;
  }
} 


Botifarra.prototype.newGame = function() {
  this.winner = -1; 
  this.turn = PLAYER1;
  //this.movements = null;
}

Botifarra.prototype.getCardScore = function(card) {
	var cardScore = 0;

        switch(card.value) {
            case "9": cardScore = 5; break; // Manilla
            case "1": cardScore = 4; break; // As
            case "K": cardScore = 3; break; // Rei
            case "Q": cardScore = 2; break; // Caball
            case "J": cardScore = 1; break; // Sota
        }

        return cardScore;
}

Botifarra.prototype.getCardPriority = function(card) {
  var priority = this.getCardScore(card);
  if(priority == 0) priority = parseInt(card.value);
  else priority += 10;

  return priority;
}

Botifarra.prototype.isValidAction = function(actionSlot, actionName, actionValue, privateCards) {
  var valid = false;
  switch(actionName) {
  case "INIT":
    valid = true;
    break;
  case "START":
    valid = (this.owner == actionValue && (this.step == null || this.step == "" || this.step == "INIT")); 
    break;
  case "RESIGN":
    valid = (this.winner < 0);
    break;
  case "CLAIM_VICTORY":
    valid = (this.winner < 0);
    break;
  case "SET_TRUMP":
  case "REJECT_DOUBLE": 
  case "ACCEPT_DOUBLE": 
    valid = ((this.turn - 1) == actionSlot);
    break;
  case "EXPOSE":
    if( (this.turn - 1) == actionSlot) {
      if(this.step == "trump") {
        valid = true;
      } else if(this.step.indexOf("double") != -1) {
        valid = true;
      } else if(this.step == "play") {
        // check game rules, requiring to win to opponent card
        var winnerSlot = -1;
        var winnerCard = null;
        var count = 0;
        var roundSuit = "";
        var actionCardParts = actionValue.split("_");
        var actionCard = { "value": actionCardParts[0], "type": actionCardParts[1] };
        var current = this.currentRound.split(',');

        // search first round card/slot
        var slot = actionSlot;
        while(count < 4) {
          slot = (slot + 4 - 1) % 4;  // positive
  	  if(current[slot] == "NONE") break;
  	  else {
		var cardParts = current[slot].split("_");
		winnerSlot = slot;
		winnerCard = { "value": cardParts[0], "type": cardParts[1] };
		roundSuit = winnerCard.type;
	  }
	  count++;
        }

        if(winnerCard == null || count == 4) {
	      valid = true;  // first card of round
        } else {
	      // search best round card
	      slot = winnerSlot;
	      while(slot != actionSlot) {
		var cardParts = current[slot].split("_");
		var card = { "value": cardParts[0], "type": cardParts[1] };

                if(winnerCard == null 
                    || (winnerCard.type != this.trump && card.type == this.trump)                     
                    || (card.type == winnerCard.type && this.getCardPriority(card) > this.getCardPriority(winnerCard) ) ) {
		    winnerCard = card;
		    winnerSlot = slot;
	        }
 
	        slot = (slot + 1) % 4;
	      }

	      // basic checks
	      var i = 0;
              var hasActionCard = false;
              var hasCardOfRoundSuit = false;
	      var hasBetterCard = false;
	      var hasBetterCardOfRoundSuit = false;
              var hasTrumpCard = false;
	      for(i = 0; i < privateCards.length; i++) {
		if(privateCards[i].type == actionCard.type && privateCards[i].value == actionCard.value) {
		   hasActionCard = true;
		}
                if(privateCards[i].type == roundSuit) {
		   hasCardOfRoundSuit = true;
                   if( (privateCards[i].type == roundSuit && privateCards[i].type == winnerCard.type && this.getCardPriority(privateCards[i]) > this.getCardPriority(winnerCard) ) ) {
		     hasBetterCardOfRoundSuit = true;
		   }
		}
                if(privateCards[i].type == this.trump) {
                   hasTrumpCard = true;
                }
	      }

	      if(!hasCardOfRoundSuit) {
		for(i = 0; i < privateCards.length; i++) {
		  if( privateCards[i].type == this.trump 
		      && (winnerCard.type != this.trump || this.getCardPriority(privateCards[i]) > this.getCardPriority(winnerCard) ) ) {
		    hasBetterCard = true;
		  }
		}
	      }

	      if(!hasActionCard) {
	        valid = false;
	      } else {

	        // validate actionValue card
		var isPartnerWinning = false;
	        if( (winnerSlot % 2) == (actionSlot % 2) ) {
	          // partner rules
		  isPartnerWinning = true;
		  valid = (actionCard.type == roundSuit || !hasCardOfRoundSuit);
	        } else {
 	          // opponent rules
		  valid = ( (hasCardOfRoundSuit && actionCard.type == roundSuit && (!hasBetterCardOfRoundSuit || (actionCard.type == winnerCard.type && this.getCardPriority(actionCard) > this.getCardPriority(winnerCard))) )
			    || (!hasCardOfRoundSuit && (!hasBetterCard || (winnerCard.type != this.trump && actionCard.type == this.trump) || (actionCard.type == winnerCard.type && this.getCardPriority(actionCard) > this.getCardPriority(winnerCard)))) )

	        }

                if(valid && (this.variant == OBLIGATORY_VARIANT || this.variant == INTERMEDIATE_VARIANT)) {
		  // check card is of round type or trump when available.
		  var requiredType = roundSuit;
		  if(!hasCardOfRoundSuit) {
		    if(hasTrumpCard && !isPartnerWinning && hasBetterCard) requiredType = this.trump;
		    else requiredType = actionCard.type;  // any
		  }

		  // check if it is a winner card, or scoring card or lowest card of the round type 
		  if(requiredType != actionCard.type) {
		    valid = false;
		  } else if(isPartnerWinning && this.getCardScore(actionCard) > 0) {
		    valid = true;
		  } else if( this.variant == OBLIGATORY_VARIANT 
			     && ((!isPartnerWinning && actionCard.type == winnerCard.type && this.getCardPriority(actionCard) < this.getCardPriority(winnerCard)) 
				 || (!isPartnerWinning && actionCard.type != roundSuit && actionCard.type != this.trump)
				 || (isPartnerWinning && this.getCardScore(actionCard) == 0)) ) {
		    // check it is the lowest card in the suit when card is not winning the round or partner is winning but played card doesn't provide more points 
		    // (except for FREE and INTERMEDIATE variants)
		    var lowestPriority = 1000;
		    var type = actionCard.type;
		    for(i = 0; i < privateCards.length; i++) {
		      var priority = this.getCardPriority(privateCards[i]);
	  	      if(privateCards[i].type == type && lowestPriority > priority) lowestPriority = priority;
                    }
		    if(this.getCardPriority(actionCard) > lowestPriority) {
		      valid = false;
		    }
	  	  }
	        }
	      }

        }
       
      }
    }
    break;
  }
  return valid;
}


return Botifarra;
})();
