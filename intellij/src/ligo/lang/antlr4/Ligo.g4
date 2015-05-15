grammar Ligo;

program: statement*;
statement: assign | call;
assign: (ID DOT)* name=ID ASSIGN_OP value=expression;
call: name=ID OPEN_PAR (expression (COMMA expression)*)? CLOSE_PAR;
expression: (id | number | string | object | call) accessChain;
accessChain: (DOT id)*;
id: ID;
number: NUMBER;
string: STRING;
object: OPEN_BRA statement* CLOSE_BRA;

OPEN_BRA: '{';
CLOSE_BRA: '}';
ASSIGN_OP: '=';
DOT: '.';
COMMA: ',';
OPEN_PAR: '(';
CLOSE_PAR: ')';
fragment DIGIT: [0-9];
fragment LETTER: [A-Z]|[a-z];
ID: (LETTER | '_') (LETTER | '_' | DIGIT)*;
NUMBER: DIGIT+ (DOT DIGIT+)?;
STRING: '"' (EscapeSequence | ~[\\"])* '"';
fragment HexDigit: [0-9a-fA-F];
fragment EscapeSequence: '\\' [btnfr"'\\] | UnicodeEscape | OctalEscape;
fragment OctalEscape: '\\' [0-3] [0-7] [0-7] | '\\' [0-7] [0-7] | '\\' [0-7];
fragment UnicodeEscape: '\\' 'u' HexDigit HexDigit HexDigit HexDigit;

WS: [ \n\t\r]+ -> skip;
SINGLE_LINE_COMMENT: '//' ~('\r' | '\n')* -> skip;
MULTI_LINE_COMMENT: '/*' .*? '*/' -> skip;