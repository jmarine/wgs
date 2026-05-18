/*
    WebGL 8x8 board games
    Copyright (C) 2011 by Jordi Mariné Fort

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

function Botifarra(points) {
  if(!points) points = 101;
  this.points = points;
  return this;
}



Botifarra.prototype = new app.model.Game();
Botifarra.prototype.constructor = Botifarra;
Botifarra.prototype.constructor.name = "Botifarra";

Botifarra.prototype.initFromStateStr = function(str) {
   var parts = [];
   if(str) parts = str.split('#');

   this.points = parts[1];
   this.turn = parseInt(parts[2]) + 1;
   this.step = parts[3];
   this.trump = parts[4];
   this.multiplier = parts[5];
   this.ask_pair_to_double = parts[6];
   this.score_team1 = parts[7];
   this.score_team2 = parts[8];
   this.currentRound = parts[9];
   this.lastRound = parts[10];
   this.owner = parts[11];

}

Botifarra.prototype.toString = function() {
   var retval = [];
   retval.push("Botifarra");
   retval.push(this.points);
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


Botifarra.prototype.newGame = function() {
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
  debugger;
  var valid = false;
  switch(actionName) {
  case "INIT":
    valid = true;
    break;
  case "START":
    valid = (this.owner == actionValue && (this.step == null || this.step == "" || this.step == "INIT")); 
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
        var roundTrump = "";
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
		roundTrump = winnerCard.type;
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
              var hasCard = false;
              var hasRoundTrump = false;
	      var hasBetterCardOfRoundTrump = false;
              var hasGameTrump = false;
	      for(var i = 0; i < privateCards.length; i++) {
		if(privateCards[i].type == actionCard.type && privateCards[i].value == actionCard.value) {
		   hasCard = true;
		}
                if(privateCards[i].type == roundTrump) {
		   hasRoundTrump = true;
                   if( (privateCards[i].type == roundTrump && privateCards[i].type == winnerCard.type && this.getCardPriority(privateCards[i]) > this.getCardPriority(winnerCard) ) ) {
		     hasBetterCardOfRoundTrump = true;
		   }
		}
                if(privateCards[i].type == this.trump) {
                   hasGameTrump = true;
                }
	      }

	      // validate actionValue card
	      if( (winnerSlot % 2) == (actionSlot % 2) ) {
	        // partner rules
		valid = hasCard && (actionCard.type == roundTrump || !hasRoundTrump);
	      } else {
 	        // opponent rules
		valid = hasCard 
			&& ( (!hasRoundTrump && !hasBetterCardOfRoundTrump && (!hasGameTrump || actionCard.type == this.trump) )
			     || (hasRoundTrump && !hasBetterCardOfRoundTrump && actionCard.type == roundTrump)
			     || (hasRoundTrump && actionCard.type == winnerCard.type && this.getCardPriority(actionCard) > this.getCardPriority(winnerCard)) );
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
