; Format of FP-number in memory:
; addr+0: Exponent=bit15..9, Sign of Mantissa=bit0
; addr+1: D1=bit15..12, D2=bit11..8, D3=bit7..4, D4=bit3..0
; addr+2: D5=bit15..12, D6=bit11..8, D7=bit7..4, D8=bit3..0
; addr+3: D9=bit15..12, D10=bit11..8, D11=bit7..4, D12=bit3..0
;
01001	020004		LDA D4		; A = addr of X
01002	024015		LDB D15		; B = addr of AR1
01003	170004		XFR		; transfer X->AR1
01004	074742		SBR 16		; B = 0
01005	035717		STB D1717	; (BTMP) = B
01006	021744		LDA D1744	; A = Exponent word of AR1
01007	070113		SAP *+2		; A>=0?
01010	067024		JMP J1024	; If not: address=0
01011	070342	J1011	SAR 8		; A = Exponent byte
01012	024063		LDB D63		; B = 1
01013	070037		ADB A		; B = A+1
01014	000023		ADA D23		; A = A-5
01015	070112		SAM *+2		; Exponent >4 ?
01016	025065		LDB D1065	; B = 5		; max. 5 digits
01017	035716	J1017	STB D1716	; (addrC) = B	; Address counter
01020	175400	J1020	DLS		; get highest digit of AR1 and shift AR1 left
01021	063056		JSM J1056	; find binary equivalent
01022	055716		DSZ D1716	; all digits processed?
01023	067020		JMP J1020	; no, repeat
;
01024	074537	J1024	LDB B,I		; B = (B)	; load contents of memory location in B
01025	035717		STB D1717	; (BTEMP) = B
01026	020016		LDA D16		; A = addr of AR2
01027	170000		CLR		; Clear AR2
01030	021066		LDA D1066	; A = 16	; process 16 bits
01031	031716		STA D1716	; initialize counter
01032	025717	J1032	LDB D1717	; B = (BTEMP)
01033	074133		SBP *+2,C	; Is bit15 of B zero?
01034	072075		SEC *+1,S	; Set E to 1111 binary
01035	074744		SBL 1		; B = B<<1
01036	035717		STB D1717	; (BTEMP) = B
01037	020016		LDA D16		; A = addr of AR2
01040	024015		LDB D15		; B = addr of AR1
01041	170004		XFR		; transfer AR2->AR1
01042	170560		FXA		; AR2 = AR2+AR1+E = 2*AR2 + bit15(BTEMP)
01043	055716		DSZ D1716	; all bits processed?
01044	067032		JMP J1032	; no, continue
01045	171450		NRM		; normalize AR2
01046	074056		CMB		; B = !B = -B-1
01047	004106		ADB D106	; B = 12-B-1 = 11-B
01050	074404		SBL 8		; B = B<<8	; exponent byte
01051	035754		STB D1754	; Exponent word of AR2 = B
01052	020016		LDA D16		; A=addr of AR2
01053	024004		LDB D4		; B=addr of X
01054	170004		XFR		; transfer AR2->X
01055	170402		RET
;
01056	025717	J1056	LDB D1717	; B = (BTEMP)
01057	074704		SBL 2		; B = 4*B
01060	005717		ADB D1717	; B = B+BTEMP = 5*BTEMP
01061	074037		ADB B		; B = 10*BTEMP
01062	070037		ADB A		; B = B+A = 10*BTEMP+A
01063	035717		STB D1717	; (BTEMP) = B
01064	170402		RET
;
01065	000005	D1065	OCT 00005	; 5
01066	000020	D1066	OCT 00020	; 16
