001750	4		4		; Execution Jump Table: +4 for Opcode=1
001751	50105		"PE"	
001752	42513		"EK"	
001753	140400		OPCODE=1	; Opcode-Bit(0x80) + Last-Opcode(0x40) + 1
001754	060445		JSM XFAR2+1	; Transfer Argument -> AR2
001755	160225		JSM FLTRA,I	; Convert Float AR2 -> Int B
001756	074742		SBR 16		; If Overflow: B=0
001757	074537		LDB B,I	; B=(B) ; load contents of memory location in B 
001760	164227		JMP FXFLA,I	; Convert Int to Float ->AR2 and return
001761	000000				
001762	000000				
001763	000000				
001764	000000				
001765	000000				
001766	000000				
001767	000000				
001770	000000				
001771	000000				
001772	177777				; Statement Jump Table
001772	177777				; Ptr. To Non-fomula Operator Mnemonic Table
001772	177755				; Ptr. To Function Mnemonic Table
001775	177775				; Ptr. To Command Mnemonic Table
001776	177774				; Ptr. To Statement Mnemonic Table
001777	060000				; Module Operation Code 58000 (not 56000!)
