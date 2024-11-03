sap.ui.define([
], function () {
    "use strict";
    return {

        makeCurrentDateTime: function () {
            const today = new Date();
            const date = today.getFullYear() + '-' + ("0" + (today.getMonth() + 1)).slice(-2) + '-' + ("0" + today.getDate()).slice(-2);
            const time = today.getHours() + ":" + today.getMinutes() + ":" + today.getSeconds() + "." + today.getMilliseconds() + "Z";
            return date + 'T' + time;
        },
    };
}, true);