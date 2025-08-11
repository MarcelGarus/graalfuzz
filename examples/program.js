function sum(list) {
    var sum = 0;
    for (item of list) {
        sum += item;
    }
    return sum;
}

function length(list) {
    if (!list) return 0;
    1 + length(list.next);
}

function isLong(list) {
    return length(list) > 5;
}

isLong
