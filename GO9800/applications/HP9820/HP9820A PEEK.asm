; Format of FP-number in memory:
; addr+0: Exponent=bit15..9, Sign of Mantissa=bit0
; addr+1: D1=bit15..12, D2=bit11..8, D3=bit7..4, D4=bit3..0
; addr+2: D5=bit15..12, D6=bit11..8, D7=bit7..4, D8=bit3..0
; addr+3: D9=bit15..12, D10=bit11..8, D11=bit7..4, D12=bit3..0
;
16426	016430		OCT D16430	; Pointer to first name
16427	177777		-1		; End of list
16430	050105	D16430	"PE"
16431	042513		"EK"
16432	177773		-4-1		; -(len+1) = End of name
16433	016434		J16434		; Pointer to program start
16434	020442	J16434	LDA D442	; A = addr of A-reg.
16435	000451		ADA D451	; A = A+024 = addr of Z-reg.
16436	024447		LDB D447	; B = addr of AR1
16437	170004		XFR		; transfer Z->AR1
16440	074742		SBR 16		; B = 0
16441	035717		STB D1717	; (BTMP) = B
16442	021744		LDA D1744	; A = Exponent word of AR1
16443	070113		SAP *+2		; A>=0?
16444	067460		JMP J16460	; If not: address=0
16445	070342	J16445	SAR 8		; A = Exponent byte
16446	024403		LDB D403	; B = 1
16447	070037		ADB A		; B = A+1
16450	000411		ADA D411	; A = A-5
16451	070112		SAM *+2		; Exponent >4 ?
16452	024377		LDB D377	; B = 5		; max. 5 digits
16453	035716	J16453	STB D1716	; (addrC) = B	; Address counter
16454	175400	J16454	DLS		; get highest digit of AR1 and shift AR1 left
16455	062513		JSM J16513	; find binary equivalent
16456	055716		DSZ D1716	; all digits processed?
16457	066454		JMP J16454	; no, repeat
;
16460	074537	J16460	LDB B,I		; B = (B)	; load contents of memory location in B
16461	035717		STB D1717	; (BTEMP) = B
16462	020450		LDA D450	; A = addr of AR2
16463	170000		CLR		; Clear AR2
16464	020364		LDA D364	; A = 16	; process 16 bits
16465	031716		STA D1716	; initialize counter
16466	025717	J16466	LDB D1717	; B = (BTEMP)
16467	074133		SBP *+2,C	; Is bit15 of B zero?
16470	072075		SEC *+1,S	; Set E to 1111 binary
16471	074744		SBL 1		; B = B<<1
16472	035717		STB D1717	; (BTEMP) = B
16473	020450		LDA D450	; A = addr of AR2
16474	024447		LDB D447	; B = addr of AR1
16475	170004		XFR		; transfer AR2->AR1
16476	170560		FXA		; AR2 = AR2+AR1+E = 2*AR2 + bit15(BTEMP)
16477	055716		DSZ D1716	; all bits processed?
16500	066466		JMP J16466	; no, continue
16501	171450		NRM		; normalize AR2
16502	074056		CMB		; B = !B = -B-1
16503	004370		ADB D370	; B = 12-B-1 = 11-B
16504	074404		SBL 8		; B = B<<8	; exponent byte
16505	035754		STB D1754	; Exponent word of AR2 = B
16506	020450		LDA D450	; A=addr of AR2
16507	024442		LDB D442	; B=addr of A-reg.
16510	004451		ADB D451	; B = B+024 = addr of Z-reg.
16511	170004		XFR		; transfer AR2->Z
16512	170402		RET
;
16513	025717	J16512	LDB D1717	; B = (BTEMP)
16514	074704		SBL 2		; B = 4*B
16515	005717		ADB D1717	; B = B+BTEMP = 5*BTEMP
16516	074037		ADB B		; B = 10*BTEMP
16517	070037		ADB A		; B = B+A = 10*BTEMP+A
16520	035717		STB D1717	; (BTEMP) = B
16521	170402		RET
