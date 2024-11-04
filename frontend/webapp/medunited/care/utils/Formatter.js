sap.ui.define(["sap/ui/core/format/DateFormat"], function(DateFormat) {
	"use strict";
	return {
        formatGivenNames: function(aGivenNames){
            return aGivenNames ? aGivenNames.slice(1).join(", ") : "";
        },

        formatPatientTelecomUse: function(sUse){
            return sUse ? (sUse.charAt(0).toUpperCase() + sUse.slice(1)+ ":") : "";
        },

		formatBirthDateAndAge: function(sDate) {
			if (sDate) {

				// 19961009
                let dd = sDate.substring(6,8);
                let mm = sDate.substring(4,6);
                let yyyy = sDate.substring(0,4);

                let currentDate = new Date();
                let currentYear = currentDate.getFullYear();
                let currentMonth = currentDate.getMonth();
                let currentDay = currentDate.getDate(); 
                let calculatedAge = currentYear - yyyy;

                if (currentMonth < mm - 1) {
                    calculatedAge--;
                }
                if (mm - 1 == currentMonth && currentDay < dd) {
                    calculatedAge--;
                }
                if (calculatedAge < 0) {
                    return "Falsches geburtsdatum eingestellt! " + birthDate;
                }
                if (calculatedAge == 1) {
                    return dd + "."+mm+"."+yyyy + " - " + calculatedAge + " Jahr";
                }
				return dd + "."+mm+"."+yyyy + " - " + calculatedAge + " Jahre";
			}
			return "";
        },

        formatFullTime: function(sDate){
            if (sDate) {
				const dateFormat = DateFormat.getDateTimeInstance({
					pattern: "dd.MM.yyyy hh:mm:ss"
				});
				return dateFormat.format(new Date(sDate));
			}

			return "";
        },
        
        formatPatientPhoto: function(oFHIRAttachment){
            if(oFHIRAttachment && oFHIRAttachment.url){
                return oFHIRAttachment.url;
            } else {
                return "images/Avatar-Sizes-Custom-1.png"
            }
        }
    };
});