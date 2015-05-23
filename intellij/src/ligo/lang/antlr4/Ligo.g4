grammar Ligo;

program: statement*;
statement: assign | call | constructorDefinition | functionDefinition;
assign: 
    (ID DOT)* name=ID 
    op=(ASSIGN_OP | ASSIGN_OP_ADD | ASSIGN_OP_SUB | ASSIGN_OP_MUL | ASSIGN_OP_DIV) 
    value=expression;
call: name=ID OPEN_PAR (expression (COMMA expression)*)? CLOSE_PAR;
constructorDefinition: ID DEFINE_OP object;
functionDefinition: ID DEFINE_OP parameters? expression;
parameters: PIPE ID* PIPE;
expression: addExpression;
addExpression: mulExpression (ADD_OP mulExpression)*;
mulExpression: leafExpression (MUL_OP leafExpression)*;
leafExpression: 
    (id | number | string | call | embeddedExpression) accessChain;
embeddedExpression: OPEN_PAR expression CLOSE_PAR;
accessChain: (DOT id)*;
id: ID;
number: NUMBER;
string: STRING;
object: OPEN_BRA statement* CLOSE_BRA;

ADD_OP: '+' | '-';
MUL_OP: '*' | '/';
OPEN_BRA: '{';
CLOSE_BRA: '}';
ASSIGN_OP: '=';
ASSIGN_OP_ADD: '+=';
ASSIGN_OP_SUB: '-=';
ASSIGN_OP_MUL: '*=';
ASSIGN_OP_DIV: '/=';
DEFINE_OP: '=>';
PIPE: '|';
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